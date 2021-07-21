import { MOCK_WORKFLOW } from './../../../common/service/user/workflow-access-control/stub-workflow-grant-access.service';
import { Workflow } from './../../../common/type/workflow';
import { StubOperatorMetadataService } from './../operator-metadata/stub-operator-metadata.service';
import { JointUIService } from './../joint-ui/joint-ui.service';
import { OperatorMetadataService } from './../operator-metadata/operator-metadata.service';
import { WorkflowActionService } from './../workflow-graph/model/workflow-action.service';
import {
  mockScanPredicate, mockResultPredicate, mockPoint
} from './../workflow-graph/model/mock-workflow-data';
import { TestBed, inject } from '@angular/core/testing';

import { UndoRedoService } from './undo-redo.service';
import { WorkflowUtilService } from '../workflow-graph/util/workflow-util.service';

describe('UndoRedoService', () => {
  let service: UndoRedoService;
  let workflowActionService: WorkflowActionService;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        UndoRedoService,
        WorkflowActionService,
        WorkflowUtilService,
        JointUIService,
        { provide: OperatorMetadataService, useClass: StubOperatorMetadataService }
      ]
    });
    service = TestBed.get(UndoRedoService);
    workflowActionService = TestBed.get(WorkflowActionService);
  });

  it('should be created', inject([UndoRedoService], (injectedService: UndoRedoService) => {
    expect(injectedService).toBeTruthy();
  }));

  it('command should append to stack', () => {
    workflowActionService.handleAutoLayout(MOCK_WORKFLOW);

    expect(service.getUndoLength()).toEqual(1);
    expect(service.getRedoLength()).toEqual(0);
  });

  it('executing command should append to stack', () => {
    workflowActionService.addOperator(mockScanPredicate, mockPoint);

    expect(service.getUndoLength()).toEqual(1);
    expect(service.getRedoLength()).toEqual(0);
  });

  it('redoing command should move from undo to redo stack and vice versa', () => {
    workflowActionService.addOperator(mockScanPredicate, mockPoint);

    service.undoAction();
    expect(service.getUndoLength()).toEqual(0);
    expect(service.getRedoLength()).toEqual(1);

    service.redoAction();
    expect(service.getUndoLength()).toEqual(1);
    expect(service.getRedoLength()).toEqual(0);
  });

  it('executing new action clears redo stack', () => {
    workflowActionService.addOperator(mockScanPredicate, mockPoint);

    service.undoAction();
    expect(service.getUndoLength()).toEqual(0);
    expect(service.getRedoLength()).toEqual(1);

    workflowActionService.addOperator(mockResultPredicate, mockPoint);
    expect(service.getUndoLength()).toEqual(1);
    expect(service.getRedoLength()).toEqual(0);
  });
});
