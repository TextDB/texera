import { Injectable } from '@angular/core';

import * as joint from 'jointjs';
import { cloneDeep } from 'lodash';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { Workflow, WorkflowContent } from '../../../../common/type/workflow';
import { mapToRecord, recordToMap } from '../../../../common/util/map';
import { WorkflowMetadata } from '../../../../dashboard/type/workflow-metadata.interface';
import { Breakpoint, OperatorLink, OperatorPort, OperatorPredicate, Point } from '../../../types/workflow-common.interface';
import { JointUIService } from '../../joint-ui/joint-ui.service';
import { OperatorMetadataService } from '../../operator-metadata/operator-metadata.service';
import { UndoRedoService } from '../../undo-redo/undo-redo.service';
import { WorkflowUtilService } from '../util/workflow-util.service';
import { JointGraphWrapper } from './joint-graph-wrapper';
import { Group, OperatorGroup, OperatorGroupReadonly } from './operator-group';
import { SyncOperatorGroup } from './sync-operator-group';
import { SyncTexeraModel } from './sync-texera-model';
import { WorkflowGraph, WorkflowGraphReadonly } from './workflow-graph';

export interface Command {
  modifiesWorkflow: boolean;
  execute(): void;
  undo(): void;
  redo?(): void;
}

type OperatorPosition = {
  position: Point,
  layer: number
};

type GroupInfo = {
  group: Group,
  layer: number
};

/**
 *
 * WorkflowActionService exposes functions (actions) to modify the workflow graph model of both JointJS and Texera,
 *  such as addOperator, deleteOperator, addLink, deleteLink, etc.
 * WorkflowActionService performs checks the validity of these actions,
 *  for example, throws an error if deleting an nonsexist operator
 *
 * All changes(actions) to the workflow graph should be called through WorkflowActionService,
 *  then WorkflowActionService will propagate these actions to JointModel and Texera Model automatically.
 *
 * For an overview of the services in WorkflowGraphModule, see workflow-graph-design.md
 *
 */

@Injectable({
  providedIn: 'root'
})
export class WorkflowActionService {

  private static readonly DEFAULT_WORKFLOW_NAME = 'Untitled Workflow';
  private static readonly DEFAULT_WORKFLOW = {
    name: WorkflowActionService.DEFAULT_WORKFLOW_NAME,
    wid: undefined,
    creationTime: undefined,
    lastModifiedTime: undefined
  };

  private readonly texeraGraph: WorkflowGraph;
  private readonly jointGraph: joint.dia.Graph;
  private readonly jointGraphWrapper: JointGraphWrapper;
  private readonly operatorGroup: OperatorGroup;
  private readonly syncTexeraModel: SyncTexeraModel;
  private readonly syncOperatorGroup: SyncOperatorGroup;

  private workflowModificationEnabled = true;
  private enableModificationStream = new BehaviorSubject<boolean>(true);

  private workflowMetadata: WorkflowMetadata;
  private workflowMetadataChangeSubject: Subject<void> = new Subject<void>();

  constructor(
    private operatorMetadataService: OperatorMetadataService,
    private jointUIService: JointUIService,
    private undoRedoService: UndoRedoService,
    private workflowUtilService: WorkflowUtilService
  ) {
    this.texeraGraph = new WorkflowGraph();
    this.jointGraph = new joint.dia.Graph();
    this.jointGraphWrapper = new JointGraphWrapper(this.jointGraph);
    this.operatorGroup = new OperatorGroup(this.texeraGraph, this.jointGraph, this.jointGraphWrapper,
      this.workflowUtilService, this.jointUIService);
    this.syncTexeraModel = new SyncTexeraModel(this.texeraGraph, this.jointGraphWrapper, this.operatorGroup);
    this.syncOperatorGroup = new SyncOperatorGroup(this.texeraGraph, this.jointGraphWrapper, this.operatorGroup);
    this.workflowMetadata = WorkflowActionService.DEFAULT_WORKFLOW;

    this.handleJointLinkAdd();
    this.handleJointOperatorDrag();
    this.handleHighlightedElementPositionChange();
  }

  // workflow modification lock interface (allows or prevents commands that would modify the workflow graph)
  public enableWorkflowModification() {
    this.workflowModificationEnabled = true;
    this.enableModificationStream.next(true);
    this.undoRedoService.enableWorkFlowModification();
  }

  public disableWorkflowModification() {
    this.workflowModificationEnabled = false;
    this.enableModificationStream.next(false);
    this.undoRedoService.disableWorkFlowModification();
  }

  public checkWorkflowModificationEnabled(): boolean {
    return this.workflowModificationEnabled;
  }

  public getWorkflowModificationEnabledStream(): Observable<boolean> {
    return this.enableModificationStream.asObservable();
  }

  public handleJointLinkAdd(): void {
    this.texeraGraph.getLinkAddStream().filter(() => this.undoRedoService.listenJointCommand).subscribe(link => {
      const command: Command = {
        modifiesWorkflow: true,
        execute: () => {
        },
        undo: () => this.deleteLinkWithIDInternal(link.linkID),
        redo: () => this.addLinkInternal(link)
      };
      this.executeAndStoreCommand(command);
    });
  }

  public handleJointOperatorDrag(): void {
    // save first event as starting position
    // compare starting position to final position at last event
    // command saves just the delta for undo/redo purposes
    let oldPosition: Point = {x: 0, y: 0};
    let gotOldPosition = false;
    let dragRoot: string; // element that was clicked to start the drag

    // save starting position
    this.jointGraphWrapper.getElementPositionChangeEvent()
      .filter(() => !gotOldPosition)
      .filter(() => this.undoRedoService.listenJointCommand)
      .subscribe(event => {
        oldPosition = event.oldPosition;
        gotOldPosition = true;
        dragRoot = event.elementID;
      });

    // get final event and compare positions
    this.jointGraphWrapper.getElementPositionChangeEvent()
      .filter(() => this.undoRedoService.listenJointCommand)
      .filter(value => value.elementID === dragRoot)
      .debounceTime(100) // emit only when no further events have occurred in 100ms
      .subscribe(event => {
        gotOldPosition = false;
        const offsetX = event.newPosition.x - oldPosition.x;
        const offsetY = event.newPosition.y - oldPosition.y;
        // remember currently highlighted operators and groups
        const currentHighlightedOperators = new Set(this.jointGraphWrapper.getCurrentHighlightedOperatorIDs().slice());
        const currentHighlightedGroups = this.jointGraphWrapper.getCurrentHighlightedGroupIDs().slice();

        // un-remember child operators of groups (they will move with the group's movement, so we must avoid moving them twice)
        currentHighlightedGroups.forEach(groupID => {
          this.operatorGroup.getGroup(groupID).operators.forEach((operatorInfo, operatorID) => {
            currentHighlightedOperators.delete(operatorID);
          });
        });

        const command: Command = {
          modifiesWorkflow: false,
          execute: () => {
          },
          undo: () => {
            this.jointGraphWrapper.unhighlightOperators(...this.jointGraphWrapper.getCurrentHighlightedOperatorIDs());
            this.jointGraphWrapper.unhighlightGroups(...this.jointGraphWrapper.getCurrentHighlightedGroupIDs());
            this.jointGraphWrapper.setMultiSelectMode(currentHighlightedOperators.size + currentHighlightedGroups.length > 1);
            currentHighlightedOperators.forEach(operatorID => {
              this.jointGraphWrapper.highlightOperators(operatorID);
              this.jointGraphWrapper.setElementPosition(operatorID, -offsetX, -offsetY);
            });
            currentHighlightedGroups.forEach(groupID => {
              this.jointGraphWrapper.highlightGroups(groupID);
              this.jointGraphWrapper.setElementPosition(groupID, -offsetX, -offsetY);
            });
          },
          redo: () => {
            this.jointGraphWrapper.unhighlightOperators(...this.jointGraphWrapper.getCurrentHighlightedOperatorIDs());
            this.jointGraphWrapper.unhighlightGroups(...this.jointGraphWrapper.getCurrentHighlightedGroupIDs());
            this.jointGraphWrapper.setMultiSelectMode(currentHighlightedOperators.size + currentHighlightedGroups.length > 1);
            currentHighlightedOperators.forEach(operatorID => {
              this.jointGraphWrapper.highlightOperators(operatorID);
              this.jointGraphWrapper.setElementPosition(operatorID, offsetX, offsetY);
            });
            currentHighlightedGroups.forEach(groupID => {
              this.jointGraphWrapper.highlightGroups(groupID);
              this.jointGraphWrapper.setElementPosition(groupID, offsetX, offsetY);
            });
          }
        };
        this.executeAndStoreCommand(command);
      });
  }

  /**
   * Subscribes to element position change event stream,
   *  checks if the element (operator or group) is moved by user and
   *  if the moved element is currently highlighted,
   *  if it is, moves other highlighted elements (operators and groups) along with it,
   *    links will automatically move with operators.
   *
   * If a group is highlighted, we consider the whole group as highlighted, including all the
   *  operators embedded in the group and regardless of whether or not they're actually highlighted.
   *  Thus, when a highlighted group moves, all its embedded operators move along with it.
   */
  public handleHighlightedElementPositionChange(): void {
    this.jointGraphWrapper.getElementPositionChangeEvent()
      .filter(() => this.jointGraphWrapper.getListenPositionChange())
      .filter(() => this.undoRedoService.listenJointCommand)
      .filter(movedElement => this.jointGraphWrapper.getCurrentHighlightedOperatorIDs().includes(movedElement.elementID) ||
        this.jointGraphWrapper.getCurrentHighlightedGroupIDs().includes(movedElement.elementID))
      .subscribe(movedElement => {
        const selectedElements = this.jointGraphWrapper.getCurrentHighlightedGroupIDs().slice(); // operators added to this list later
        const movedGroup = this.operatorGroup.getGroupByOperator(movedElement.elementID);

        if (movedGroup && selectedElements.includes(movedGroup.groupID)) {
          movedGroup.operators.forEach((operatorInfo, operatorID) => selectedElements.push(operatorID));
          selectedElements.splice(selectedElements.indexOf(movedGroup.groupID), 1);
        }
        this.jointGraphWrapper.getCurrentHighlightedOperatorIDs().forEach(operatorID => {
          const group = this.operatorGroup.getGroupByOperator(operatorID);
          // operators move with their groups,
          // do not add elements that are in a group that will also be moved
          if (!group || !this.jointGraphWrapper.getCurrentHighlightedGroupIDs().includes(group.groupID)) {
            selectedElements.push(operatorID);
          }
        });
        const offsetX = movedElement.newPosition.x - movedElement.oldPosition.x;
        const offsetY = movedElement.newPosition.y - movedElement.oldPosition.y;
        this.jointGraphWrapper.setListenPositionChange(false);
        this.undoRedoService.setListenJointCommand(false);
        selectedElements.filter(elementID => elementID !== movedElement.elementID).forEach(elementID =>
          this.jointGraphWrapper.setElementPosition(elementID, offsetX, offsetY));
        this.jointGraphWrapper.setListenPositionChange(true);
        this.undoRedoService.setListenJointCommand(true);
      });
  }

  /**
   * Gets the read-only version of the TexeraGraph
   *  to access the properties and event streams.
   *
   * Texera Graph contains information about the logical workflow plan of Texera,
   *  such as the types and properties of the operators.
   */
  public getTexeraGraph(): WorkflowGraphReadonly {
    return this.texeraGraph;
  }

  /**
   * Gets the JointGraph Wrapper, which contains
   *  getter for properties and event streams as RxJS Observables.
   *
   * JointJS Graph contains information about the UI,
   *  such as the position of operator elements, and the event of user dragging a cell around.
   */
  public getJointGraphWrapper(): JointGraphWrapper {
    return this.jointGraphWrapper;
  }

  /**
   * Gets the read-only version of the OperatorGroup
   *  which provides access to properties, event streams,
   *  and some helper functions.
   */
  public getOperatorGroup(): OperatorGroupReadonly {
    return this.operatorGroup;
  }

  /**
   * Adds an operator to the workflow graph at a point.
   * Throws an Error if the operator ID already existed in the Workflow Graph.
   *
   * @param operator
   * @param point
   */
  public addOperator(operator: OperatorPredicate, point: Point): void {
    // remember currently highlighted operators and groups
    const currentHighlights = this.jointGraphWrapper.getCurrentHighlights();

    const command: Command = {
      modifiesWorkflow: true,
      execute: () => {
        // turn off multiselect since there's only one operator added
        this.jointGraphWrapper.setMultiSelectMode(false);
        // add operator
        this.addOperatorInternal(operator, point);
        // highlight the newly added operator
        this.jointGraphWrapper.highlightOperators(operator.operatorID);
      },
      undo: () => {
        // remove the operator from JointJS
        this.deleteOperatorInternal(operator.operatorID);
        // restore previous highlights
        this.jointGraphWrapper.unhighlightElements(this.jointGraphWrapper.getCurrentHighlights());
        this.jointGraphWrapper.setMultiSelectMode(
          currentHighlights.operators.length + currentHighlights.groups.length + currentHighlights.links.length > 1);
        this.jointGraphWrapper.highlightElements(currentHighlights);
      }
    };
    this.executeAndStoreCommand(command);
  }

  /**
   * Deletes an operator from the workflow graph
   * Throws an Error if the operator ID doesn't exist in the Workflow Graph.
   * @param operatorID
   */
  public deleteOperator(operatorID: string): void {
    const operator = this.getTexeraGraph().getOperator(operatorID);
    const position = this.getOperatorGroup().getOperatorPositionByGroup(operatorID);
    const layer = this.getOperatorGroup().getOperatorLayerByGroup(operatorID);

    const linksToDelete = new Map<OperatorLink, number>();
    this.getTexeraGraph().getAllLinks()
      .filter(link => link.source.operatorID === operatorID || link.target.operatorID === operatorID)
      .forEach(link => linksToDelete.set(link, this.getOperatorGroup().getLinkLayerByGroup(link.linkID)));

    const group = cloneDeep(this.getOperatorGroup().getGroupByOperator(operatorID));
    const groupLayer = group ? this.getJointGraphWrapper().getCellLayer(group.groupID) : undefined;

    const command: Command = {
      modifiesWorkflow: true,
      execute: () => {
        linksToDelete.forEach((linkLayer, link) => this.deleteLinkWithIDInternal(link.linkID));
        this.deleteOperatorInternal(operatorID);
        if (group && this.getOperatorGroup().getGroup(group.groupID).operators.size < 2) {
          this.unGroupInternal(group.groupID);
        }
      },
      undo: () => {
        this.addOperatorInternal(operator, position);
        this.getJointGraphWrapper().setCellLayer(operatorID, layer);
        linksToDelete.forEach((linkLayer, link) => {
          this.addLinkInternal(link);
          this.getJointGraphWrapper().setCellLayer(link.linkID, linkLayer);
        });
        if (group && this.getOperatorGroup().hasGroup(group.groupID)) {
          this.getOperatorGroup().addOperatorToGroup(operatorID, group.groupID);
        } else if (group && groupLayer) {
          this.addGroupInternal(cloneDeep(group));
          this.getJointGraphWrapper().setCellLayer(group.groupID, groupLayer);
        }
        if (!group || !group.collapsed) {
          // turn off multiselect since only the deleted operator will be added
          this.getJointGraphWrapper().setMultiSelectMode(false);
          this.getJointGraphWrapper().highlightOperators(operatorID);
        }
      }
    };
    this.executeAndStoreCommand(command);
  }

  /**
   * Adds given operators and links to the workflow graph.
   * @param operatorsAndPositions
   * @param links
   */
  public addOperatorsAndLinks(
    operatorsAndPositions: readonly { op: OperatorPredicate, pos: Point }[],
    links?: readonly OperatorLink[],
    groups?: readonly Group[],
    breakpoints?: ReadonlyMap<string, Breakpoint>
  ): void {
    // remember currently highlighted operators and groups
    const currentHighlights = this.jointGraphWrapper.getCurrentHighlights();

    const command: Command = {
      modifiesWorkflow: true,
      execute: () => {
        // unhighlight previous highlights
        this.jointGraphWrapper.unhighlightElements(currentHighlights);
        this.jointGraphWrapper.setMultiSelectMode(operatorsAndPositions.length > 1);
        operatorsAndPositions.forEach(o => {
          this.addOperatorInternal(o.op, o.pos);
          this.jointGraphWrapper.highlightOperators(o.op.operatorID);
        });
        if (links) {
          links.forEach(l => this.addLinkInternal(l));
          if (breakpoints !== undefined) {
            breakpoints.forEach((breakpoint, linkID) => this.setLinkBreakpointInternal(linkID, breakpoint));
          }
        }

        if (groups) {
          groups.forEach(group => {
            // make a copy, because groups can be mutated after being given to operatorGroup (deletion for example)
            const groupCopy = cloneDeep(group);
            this.addGroupInternal(groupCopy);
            this.operatorGroup.moveGroupToLayer(groupCopy, this.operatorGroup.getHighestLayer() + 1);
          });
        }

      },
      undo: () => {
        if (groups) {
          groups.forEach(group => {
            this.unGroupInternal(group.groupID);
          });
        }

        // remove links
        if (links) {
          links.forEach(l => this.deleteLinkWithIDInternal(l.linkID));
        }
        // remove the operators from JointJS
        operatorsAndPositions.forEach(o => this.deleteOperatorInternal(o.op.operatorID));
        if (breakpoints !== undefined) {
          breakpoints.forEach((breakpoint, linkID) => this.setLinkBreakpointInternal(linkID, undefined));
        }
        // restore previous highlights
        this.jointGraphWrapper.unhighlightElements(this.jointGraphWrapper.getCurrentHighlights());
        this.jointGraphWrapper.setMultiSelectMode(
          currentHighlights.operators.length + currentHighlights.groups.length + currentHighlights.links.length > 1);
        this.jointGraphWrapper.highlightElements(currentHighlights);
      }
    };
    this.executeAndStoreCommand(command);
  }

  /**
   * Deletes given operators and links from the workflow graph.
   * @param operatorIDs
   * @param linkIDs
   */
  public deleteOperatorsAndLinks(operatorIDs: readonly string[], linkIDs: readonly string[], groupIDs?: readonly string[]): void {

    // combines operators in selected groups and operators explicitly
    const operatorIDsCopy = Array.from(new Set(operatorIDs.concat((groupIDs ?? []).flatMap(groupID =>
      Array.from(this.operatorGroup.getGroup(groupID).operators.values()).map(operatorInfo => operatorInfo.operator.operatorID)
    ))));

    // save operators to be deleted and their current positions
    const operatorsAndPositions = new Map<OperatorPredicate, OperatorPosition>();

    operatorIDsCopy.forEach(operatorID => operatorsAndPositions.set(this.getTexeraGraph().getOperator(operatorID),
      {
        position: this.getOperatorGroup().getOperatorPositionByGroup(operatorID),
        layer: this.getOperatorGroup().getOperatorLayerByGroup(operatorID)
      }));

    // save links to be deleted, including links explicitly deleted and implicitly deleted with their operators
    const linksToDelete = new Map<OperatorLink, number>();
    // delete links required by this command
    linkIDs.map(linkID => this.getTexeraGraph().getLinkWithID(linkID))
      .forEach(link => linksToDelete.set(link, this.getOperatorGroup().getLinkLayerByGroup(link.linkID)));
    // delete links related to the deleted operator
    this.getTexeraGraph().getAllLinks()
      .filter(link => operatorIDsCopy.includes(link.source.operatorID) || operatorIDsCopy.includes(link.target.operatorID))
      .forEach(link => linksToDelete.set(link, this.getOperatorGroup().getLinkLayerByGroup(link.linkID)));

    // save groups that deleted operators reside in
    const groups = new Map<string, GroupInfo>();
    operatorIDsCopy.forEach(operatorID => {
      const group = cloneDeep(this.getOperatorGroup().getGroupByOperator(operatorID));
      if (group) {
        groups.set(operatorID, {group, layer: this.getJointGraphWrapper().getCellLayer(group.groupID)});
      }
    });

    // remember currently highlighted operators and groups
    const currentHighlights = this.jointGraphWrapper.getCurrentHighlights();

    const command: Command = {
      modifiesWorkflow: true,
      execute: () => {
        (groupIDs ?? []).forEach(groupID => {
          this.unGroupInternal(groupID);
        });
        linksToDelete.forEach((layer, link) => this.deleteLinkWithIDInternal(link.linkID));
        operatorIDsCopy.forEach(operatorID => {
          this.deleteOperatorInternal(operatorID);
          // if the group has less than 2 operators left, delete the group
          const groupInfo = groups.get(operatorID);
          if (groupInfo && this.getOperatorGroup().hasGroup(groupInfo.group.groupID) &&
            this.getOperatorGroup().getGroup(groupInfo.group.groupID).operators.size < 2) {
            this.unGroupInternal(groupInfo.group.groupID);
          }
        });

      },
      undo: () => {
        operatorsAndPositions.forEach((pos, operator) => {
          this.addOperatorInternal(operator, pos.position);
          this.getJointGraphWrapper().setCellLayer(operator.operatorID, pos.layer);
          // if the group still exists, add the operator back to the group
          const groupInfo = groups.get(operator.operatorID);
          if (groupInfo && this.getOperatorGroup().hasGroup(groupInfo.group.groupID)) {
            this.getOperatorGroup().addOperatorToGroup(operator.operatorID, groupInfo.group.groupID);
          }
        });
        linksToDelete.forEach((layer, link) => {
          this.addLinkInternal(link);
          // if the link is added to a collapsed group, change its saved layer in the group
          const group = this.getOperatorGroup().getGroupByLink(link.linkID);
          if (group && group.collapsed) {
            const linkInfo = group.links.get(link.linkID);
            if (linkInfo) { linkInfo.layer = layer; }
          } else {
            this.getJointGraphWrapper().setCellLayer(link.linkID, layer);
          }
        });
        // add back groups that were deleted when deleting operators
        groups.forEach(groupInfo => {
          if (!this.getOperatorGroup().hasGroup(groupInfo.group.groupID)) {
            this.addGroupInternal(cloneDeep(groupInfo.group));
            this.getJointGraphWrapper().setCellLayer(groupInfo.group.groupID, groupInfo.layer);
          }
        });
        // restore previous highlights
        this.jointGraphWrapper.unhighlightElements(this.jointGraphWrapper.getCurrentHighlights());
        this.jointGraphWrapper.setMultiSelectMode(
          currentHighlights.operators.length + currentHighlights.groups.length + currentHighlights.links.length > 1);
        this.jointGraphWrapper.highlightElements(currentHighlights);
      }
    };

    this.executeAndStoreCommand(command);
  }

  /**
   * Handles the auto layout function
   *
   * @param Workflow
   */
  // Originally: drag Operator
    public handleAutoLayout(workflow: Workflow | undefined): void {
      const oldOperatorsAndPositions: { operator: OperatorPredicate, pos: Point }[] = [];
      // remeber old position
      if (workflow === undefined) {
        return;
      }
      const workflowContent: WorkflowContent = workflow.content;
      workflowContent.operators.forEach(op => {
        const oldPosition = workflowContent.operatorPositions[op.operatorID];
        oldOperatorsAndPositions.push({operator: op, pos: oldPosition});
      });
        const command: Command = {
        modifiesWorkflow: false,
        execute: () => {
          this.jointGraphWrapper.setAutoLayout();
        },
        undo: () => {
          oldOperatorsAndPositions.forEach(op => {
          this.jointGraphWrapper.setAbsolutePosition(op.operator.operatorID, op.pos.x, op.pos.y);
          });
        }
      };
      this.executeAndStoreCommand(command);
    }

  // not used I believe
  /**
   * Adds a link to the workflow graph
   * Throws an Error if the link ID or the link with same source and target already exists.
   * @param link
   */
  public addLink(link: OperatorLink): void {
    const command: Command = {
      modifiesWorkflow: true,
      execute: () => this.addLinkInternal(link),
      undo: () => this.deleteLinkWithIDInternal(link.linkID)
    };
    this.executeAndStoreCommand(command);
  }

  /**
   * Deletes a link with the linkID from the workflow graph
   * Throws an Error if the linkID doesn't exist in the workflow graph.
   * @param linkID
   */
  public deleteLinkWithID(linkID: string): void {
    const link = this.getTexeraGraph().getLinkWithID(linkID);
    const layer = this.getJointGraphWrapper().getCellLayer(linkID);
    const command: Command = {
      modifiesWorkflow: true,
      execute: () => this.deleteLinkWithIDInternal(linkID),
      undo: () => {
        this.addLinkInternal(link);
        const group = this.getOperatorGroup().getGroupByLink(linkID);
        if (group && group.collapsed) {
          const linkInfo = group.links.get(linkID);
          if (linkInfo) { linkInfo.layer = layer; }
        } else {
          this.getJointGraphWrapper().setCellLayer(linkID, layer);
        }
      }
    };
    this.executeAndStoreCommand(command);
  }

  public deleteLink(source: OperatorPort, target: OperatorPort): void {
    const link = this.getTexeraGraph().getLink(source, target);
    this.deleteLinkWithID(link.linkID);
  }

  /**
   * Adds given groups to the workflow graph.
   * @param groups
   */
  public addGroups(...groups: readonly Group[]): void {

    const command: Command = {
      modifiesWorkflow: false,
      execute: () => {
        groups.forEach(group => {
          // make a copy, because groups can be mutated after being given to operatorGroup (deletion for example)
          const groupCopy = cloneDeep(group);
          this.addGroupInternal(groupCopy);
          this.operatorGroup.moveGroupToLayer(groupCopy, this.operatorGroup.getHighestLayer() + 1);
        });
      },
      undo: () => {
        groups.forEach(group => {
          this.unGroupInternal(group.groupID);
        });
      }
    };
    this.executeAndStoreCommand(command);
  }

  /**
   * ungroups given groups from the workflow graph.
   * @param groupIDs
   */
  public unGroupGroups(...groupIDs: readonly string[]): void {
    const groups = groupIDs.map(groupID => cloneDeep(this.operatorGroup.getGroup(groupID)));

    const command: Command = {
      modifiesWorkflow: false,
      execute: () => groupIDs.forEach(groupID => this.unGroupInternal(groupID)),
      undo: () => {
        groups.forEach(group => {
          // make a copy, because groups can be mutated after being given to operatorGroup (deletion for example)
          const groupCopy = cloneDeep(group);
          this.addGroupInternal(groupCopy);
          this.operatorGroup.moveGroupToLayer(groupCopy, this.operatorGroup.getHighestLayer() + 1);
        });
      }
    };
    this.executeAndStoreCommand(command);
  }

  /**
   * Collapses given groups on the graph.
   * @param groupIDs
   */
  public collapseGroups(...groupIDs: readonly string[]): void {
    const command: Command = {
      modifiesWorkflow: false,
      execute: () => groupIDs.forEach(groupID => this.collapseGroupInternal(groupID)),
      undo: () => groupIDs.forEach(groupID => this.expandGroupInternal(groupID))
    };
    this.executeAndStoreCommand(command);
  }

  /**
   * Expands given groups on the graph.
   * @param groupIDs
   */
  public expandGroups(...groupIDs: string[]): void {
    const command: Command = {
      modifiesWorkflow: false,
      execute: () => groupIDs.forEach(groupID => this.expandGroupInternal(groupID)),
      undo: () => groupIDs.forEach(groupID => this.collapseGroupInternal(groupID))
    };
    this.executeAndStoreCommand(command);
  }

  /**
   * Deletes given groups and all operators embedded in them.
   * @param groupID
   */
  public deleteGroupsAndOperators(...groupIDs: readonly string[]): void {

    // save all explicitly deleted group operators and links
    // this is necessary because deleteGroupAndOperatorsInternal deletes operators and links,
    // trying to add back a group whose operators are gone will cause an error (referencing a deleted operator)
    const operators = groupIDs.map(groupID => Array.from(this.operatorGroup.getGroup(groupID).operators.values()));
    const links = groupIDs.map(groupID => Array.from(this.operatorGroup.getGroup(groupID).links.values()));
    const inLinks = groupIDs.map(
      groupID => this.operatorGroup.getGroup(groupID).inLinks.map(linkID => this.texeraGraph.getLinkWithID(linkID)));
    const outLinks = groupIDs.map(
      groupID => this.operatorGroup.getGroup(groupID).outLinks.map(linkID => this.texeraGraph.getLinkWithID(linkID)));

    const command: Command = {
      modifiesWorkflow: true,
      execute: () => groupIDs.forEach(groupID => this.deleteGroupAndOperatorsInternal(groupID)),
      undo: () => {
        for (let i = 0; i < groupIDs.length; i++) {
          // add back operators and links of deleted groups
          operators[i].forEach(operatorInfo => this.addOperatorInternal(operatorInfo.operator, operatorInfo.position));
          links[i].forEach(linkInfo => this.addLinkInternal(linkInfo.link));
          inLinks[i].forEach(operatorLink => this.addLinkInternal(operatorLink));
          outLinks[i].forEach(operatorLink => this.addLinkInternal(operatorLink));

          // re-create group with same operators and ID
          const recreatedGroup = this.operatorGroup.getNewGroup(
            operators[i].map(operatorInfo => operatorInfo.operator.operatorID), groupIDs[i]);

          // add back group as if normal
          this.addGroupInternal(recreatedGroup);
          this.operatorGroup.moveGroupToLayer(recreatedGroup, this.operatorGroup.getHighestLayer() + 1);
        }

      }
    };
    this.executeAndStoreCommand(command);
  }

  public setOperatorProperty(operatorID: string, newProperty: object): void {
    const prevProperty = this.getTexeraGraph().getOperator(operatorID).operatorProperties;
    const group = this.getOperatorGroup().getGroupByOperator(operatorID);
    const command: Command = {
      modifiesWorkflow: true,
      execute: () => {
        this.setOperatorPropertyInternal(operatorID, newProperty);

        // unhighlight everything but the operator being modified
        const currentHighlightedOperators = <string[]>this.jointGraphWrapper.getCurrentHighlightedOperatorIDs().slice();
        if ((!group || !group.collapsed) && !currentHighlightedOperators.includes(operatorID)) {
          this.jointGraphWrapper.setMultiSelectMode(false);
          this.jointGraphWrapper.highlightOperators(operatorID);
        } else if (!group || !group.collapsed) {
          currentHighlightedOperators.splice(currentHighlightedOperators.indexOf(operatorID), 1);
          this.jointGraphWrapper.unhighlightOperators(...currentHighlightedOperators);
          this.jointGraphWrapper.unhighlightGroups(...this.jointGraphWrapper.getCurrentHighlightedGroupIDs());
        }
      },
      undo: () => {
        this.setOperatorPropertyInternal(operatorID, prevProperty);

        // unhighlight everything but the operator being modified
        const currentHighlightedOperators = <string[]>this.jointGraphWrapper.getCurrentHighlightedOperatorIDs().slice();
        if ((!group || !group.collapsed) && !currentHighlightedOperators.includes(operatorID)) {
          this.jointGraphWrapper.setMultiSelectMode(false);
          this.jointGraphWrapper.highlightOperators(operatorID);
        } else if (!group || !group.collapsed) {
          currentHighlightedOperators.splice(currentHighlightedOperators.indexOf(operatorID), 1);
          this.jointGraphWrapper.unhighlightOperators(...currentHighlightedOperators);
          this.jointGraphWrapper.unhighlightGroups(...this.jointGraphWrapper.getCurrentHighlightedGroupIDs());
        }
      }
    };
    this.executeAndStoreCommand(command);
  }

  /**
   * set a given link's breakpoint properties to specific values
   */
  public setLinkBreakpoint(linkID: string, newBreakpoint: Breakpoint | undefined): void {
    const prevBreakpoint = this.getTexeraGraph().getLinkBreakpoint(linkID);
    const command: Command = {
      modifiesWorkflow: true,
      execute: () => {
        this.setLinkBreakpointInternal(linkID, newBreakpoint);
      },
      undo: () => {
        this.setLinkBreakpointInternal(linkID, prevBreakpoint);
      }
    };
    this.executeAndStoreCommand(command);
  }

  /**
   * Set the link's breakpoint property to empty to remove the breakpoint
   *
   * @param linkID
   */
  public removeLinkBreakpoint(linkID: string): void {
    this.setLinkBreakpoint(linkID, undefined);
  }

  /**
   * Reload the given workflow, update workflowMetadata and workflowContent.
   */
  public reloadWorkflow(workflow: Workflow | undefined): void {
    this.setWorkflowMetadata(workflow);
    // remove the existing operators on the paper currently
    this.deleteOperatorsAndLinks(
      this.getTexeraGraph().getAllOperators().map(op => op.operatorID), []);

    if (workflow === undefined) {
      return;
    }

    const workflowContent: WorkflowContent = workflow.content;

    const operatorsAndPositions: { op: OperatorPredicate, pos: Point }[] = [];
    workflowContent.operators.forEach(op => {
      const opPosition = workflowContent.operatorPositions[op.operatorID];
      if (!opPosition) {
        throw new Error('position error');
      }
      operatorsAndPositions.push({op: op, pos: opPosition});
    });

    const links: OperatorLink[] = workflowContent.links;

    const groups: readonly Group[] = workflowContent.groups.map(group => {
      return {
        groupID: group.groupID, operators: recordToMap(group.operators),
        links: recordToMap(group.links), inLinks: group.inLinks, outLinks: group.outLinks,
        collapsed: group.collapsed
      };
    });

    const breakpoints = new Map(Object.entries(workflowContent.breakpoints));

    this.addOperatorsAndLinks(operatorsAndPositions, links, groups, breakpoints);

    // operators shouldn't be highlighted during page reload
    const jointGraphWrapper = this.getJointGraphWrapper();
    jointGraphWrapper.unhighlightOperators(
      ...jointGraphWrapper.getCurrentHighlightedOperatorIDs());
    // restore the view point
    this.getJointGraphWrapper().restoreDefaultZoomAndOffset();
  }

  public workflowChanged(): Observable<void> {
    return Observable.merge(
      this.getTexeraGraph().getOperatorAddStream(),
      this.getTexeraGraph().getOperatorDeleteStream(),
      this.getTexeraGraph().getLinkAddStream(),
      this.getTexeraGraph().getLinkDeleteStream(),
      this.getOperatorGroup().getGroupAddStream(),
      this.getOperatorGroup().getGroupDeleteStream(),
      this.getOperatorGroup().getGroupCollapseStream(),
      this.getOperatorGroup().getGroupExpandStream(),
      this.getTexeraGraph().getOperatorPropertyChangeStream(),
      this.getTexeraGraph().getBreakpointChangeStream(),
      this.getJointGraphWrapper().getElementPositionChangeEvent(),
      this.getTexeraGraph().getDisabledOperatorsChangedStream(),
    );
  }

  public workflowMetaDataChanged(): Observable<void> {
    return this.workflowMetadataChangeSubject.asObservable();
  }

  public setWorkflowMetadata(workflowMetaData: WorkflowMetadata | undefined): void {
    this.workflowMetadata = (workflowMetaData === undefined) ? WorkflowActionService.DEFAULT_WORKFLOW : workflowMetaData;
    this.workflowMetadataChangeSubject.next();
  }

  public getWorkflowMetadata(): WorkflowMetadata {
    return this.workflowMetadata;
  }

  public getWorkflowContent(): WorkflowContent {
    // collect workflow content
    const texeraGraph = this.getTexeraGraph();
    const operators = texeraGraph.getAllOperators();
    const links = texeraGraph.getAllLinks();
    const operatorPositions: { [key: string]: Point } = {};

    const groups = this.getOperatorGroup().getAllGroups().map(group => {
      return {
        groupID: group.groupID, operators: mapToRecord(group.operators),
        links: mapToRecord(group.links), inLinks: group.inLinks, outLinks: group.outLinks,
        collapsed: group.collapsed
      };
    });
    const breakpointsMap = texeraGraph.getAllLinkBreakpoints();
    const breakpoints: Record<string, Breakpoint> = {};
    breakpointsMap.forEach((value, key) => (breakpoints[key] = value));
    texeraGraph.getAllOperators().forEach(op => operatorPositions[op.operatorID] =
      this.getJointGraphWrapper().getElementPosition(op.operatorID));

    const workflowContent: WorkflowContent = {
      operators, operatorPositions, links, groups, breakpoints
    };
    return workflowContent;
  }

  public getWorkflow(): Workflow {
    return {...this.workflowMetadata, ...{content: this.getWorkflowContent()}};
  }

  public setWorkflowName(name: string): void {
    this.workflowMetadata.name = name.trim().length > 0 ? name : WorkflowActionService.DEFAULT_WORKFLOW_NAME;
    this.workflowMetadataChangeSubject.next();
  }

  public resetAsNewWorkflow() {
    this.reloadWorkflow(undefined);
    this.undoRedoService.clearUndoStack();
    this.undoRedoService.clearRedoStack();
  }

  private addOperatorInternal(operator: OperatorPredicate, point: Point): void {
    // check that the operator doesn't exist
    this.texeraGraph.assertOperatorNotExists(operator.operatorID);
    // check that the operator type exists
    if (!this.operatorMetadataService.operatorTypeExists(operator.operatorType)) {
      throw new Error(`operator type ${operator.operatorType} is invalid`);
    }
    // get the JointJS UI element for operator
    const operatorJointElement = this.jointUIService.getJointOperatorElement(operator, point);

    // add operator to joint graph first
    // if jointJS throws an error, it won't cause the inconsistency in texera graph
    this.jointGraph.addCell(operatorJointElement);
    this.jointGraphWrapper.setCellLayer(operator.operatorID, this.operatorGroup.getHighestLayer() + 1);

    // add operator to texera graph
    this.texeraGraph.addOperator(operator);
  }

  private deleteOperatorInternal(operatorID: string): void {
    this.texeraGraph.assertOperatorExists(operatorID);
    const group = this.operatorGroup.getGroupByOperator(operatorID);
    if (group && group.collapsed) {
      this.texeraGraph.deleteOperator(operatorID);
    } else {
      // remove the operator from JointJS
      this.jointGraph.getCell(operatorID).remove();
      // JointJS operator delete event will propagate and trigger Texera operator delete
    }
  }

  private addLinkInternal(link: OperatorLink): void {
    this.texeraGraph.assertLinkNotExists(link);
    this.texeraGraph.assertLinkIsValid(link);

    const sourceGroup = this.operatorGroup.getGroupByOperator(link.source.operatorID);
    const targetGroup = this.operatorGroup.getGroupByOperator(link.target.operatorID);

    if (sourceGroup && targetGroup && sourceGroup.groupID === targetGroup.groupID && sourceGroup.collapsed) {
      this.texeraGraph.addLink(link);
    } else {
      // if a group is collapsed, jointjs target is the group not the operator
      const jointLinkCell = JointUIService.getJointLinkCell(link);
      if (sourceGroup && sourceGroup.collapsed) {
        jointLinkCell.set('source', {id: sourceGroup.groupID});
      }
      if (targetGroup && targetGroup.collapsed) {
        jointLinkCell.set('target', {id: targetGroup.groupID});
      }

      // manually add a link element (normally automatic when syncTexeraGraph = true)
      this.operatorGroup.setSyncTexeraGraph(false);
      this.jointGraph.addCell(jointLinkCell);
      this.jointGraphWrapper.setCellLayer(link.linkID, this.operatorGroup.getHighestLayer() + 1);
      this.operatorGroup.setSyncTexeraGraph(true);

      this.texeraGraph.addLink(link);
    }
  }

  private deleteLinkWithIDInternal(linkID: string): void {
    this.texeraGraph.assertLinkWithIDExists(linkID);

    const group = this.operatorGroup.getGroupByLink(linkID);
    if (group && group.collapsed) {
      this.texeraGraph.deleteLinkWithID(linkID);
    } else {
      this.jointGraph.getCell(linkID).remove();
      // JointJS link delete event will propagate and trigger Texera link delete
    }
  }

  private addGroupInternal(group: Group): void {
    this.operatorGroup.assertGroupNotExists(group.groupID);
    this.operatorGroup.assertGroupIsValid(group);

    // get the JointJS UI element for the group and add it to joint graph
    const groupJointElement = this.jointUIService.getJointGroupElement(group, this.operatorGroup.getGroupBoundingBox(group));
    this.jointGraph.addCell(groupJointElement);

    // add the group to group ID map
    this.operatorGroup.addGroup(group);

    // collapse the group if it's specified as collapsed
    if (group.collapsed) {
      this.operatorGroup.setGroupCollapsed(group.groupID, false);
      this.collapseGroupInternal(group.groupID);
    }
  }

  private unGroupInternal(groupID: string): void {
    const group = this.operatorGroup.getGroup(groupID);

    // if the group is collapsed, expand it before ungrouping
    if (group.collapsed) {
      this.expandGroupInternal(groupID);
    }

    // delete the group from joint graph
    const groupJointElement = this.jointGraph.getCell(groupID);
    groupJointElement.remove();

    // delete the group from group ID map
    this.operatorGroup.unGroup(groupID);
  }

  private collapseGroupInternal(groupID: string): void {
    const group = this.operatorGroup.getGroup(groupID);
    this.operatorGroup.assertGroupNotCollapsed(group);

    // collapse the group on joint graph
    this.jointGraphWrapper.setElementSize(groupID, 170, 30);
    this.operatorGroup.hideOperatorsAndLinks(group);

    // update the group in OperatorGroup
    this.operatorGroup.collapseGroup(groupID);
  }

  private expandGroupInternal(groupID: string): void {
    const group = this.operatorGroup.getGroup(groupID);
    this.operatorGroup.assertGroupIsCollapsed(group);

    // expand the group on joint graph
    this.operatorGroup.repositionGroup(group);
    this.operatorGroup.showOperatorsAndLinks(group);

    // update the group in OperatorGroup
    this.operatorGroup.expandGroup(groupID);
  }

  private deleteGroupAndOperatorsInternal(groupID: string): void {
    const group = this.operatorGroup.getGroup(groupID);
    // delete operators and links from the group
    group.links.forEach((linkInfo, linkID) => this.deleteLinkWithIDInternal(linkID));
    group.inLinks.forEach(linkID => this.deleteLinkWithIDInternal(linkID));
    group.outLinks.forEach(linkID => this.deleteLinkWithIDInternal(linkID));
    group.operators.forEach((operatorInfo, operatorID) => this.deleteOperatorInternal(operatorID));
    // delete the group from joint graph and group ID map
    this.jointGraph.getCell(groupID).remove();
    this.operatorGroup.unGroup(groupID);
  }

  // use this to modify properties
  private setOperatorPropertyInternal(operatorID: string, newProperty: object) {
    this.texeraGraph.setOperatorProperty(operatorID, newProperty);
  }

  private executeAndStoreCommand(command: Command): void {

    // if command would modify workflow (adding link, operator, changing operator properties), throw an error
    // non-modifying commands include dragging an operator.
    if (command.modifiesWorkflow && !this.workflowModificationEnabled) {
      console.error('attempted to execute workflow action when workflow service is disabled');
      return;
    }

    this.undoRedoService.setListenJointCommand(false);
    command.execute();
    this.undoRedoService.addCommand(command);
    this.undoRedoService.setListenJointCommand(true);
  }

  private setLinkBreakpointInternal(linkID: string, newBreakpoint: Breakpoint | undefined): void {
    this.texeraGraph.setLinkBreakpoint(linkID, newBreakpoint);
    if (newBreakpoint === undefined || Object.keys(newBreakpoint).length === 0) {
      this.getJointGraphWrapper().hideLinkBreakpoint(linkID);
    } else {
      this.getJointGraphWrapper().showLinkBreakpoint(linkID);
    }
  }

}

