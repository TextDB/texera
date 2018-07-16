import { StubOperatorMetadataService } from '../../operator-metadata/stub-operator-metadata.service';
import { OperatorMetadataService } from '../../operator-metadata/operator-metadata.service';
import { JointUIService } from '../../joint-ui/joint-ui.service';
import { WorkflowGraph } from './workflow-graph';
import {
  mockScanPredicate, mockResultPredicate, mockSentimentPredicate, mockScanResultLink,
  mockScanSentimentLink, mockSentimentResultLink, mockFalseResultSentimentLink, mockFalseSentimentScanLink,
  mockPoint
} from '../../../mock-data/mock-workflow-data';
import { TestBed, inject } from '@angular/core/testing';

import { WorkflowActionService } from './workflow-action.service';
import { OperatorPredicate } from '../../../types/workflow-common.interface';

describe('WorkflowActionService', () => {

  let service: WorkflowActionService;
  let texeraGraph: WorkflowGraph;
  let jointGraph: joint.dia.Graph;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        WorkflowActionService,
        JointUIService,
        { provide: OperatorMetadataService, useClass: StubOperatorMetadataService }
      ]
    });
    service = TestBed.get(WorkflowActionService);
    texeraGraph = (service as any).texeraGraph;
    jointGraph = (service as any).jointGraph;
  });

  it('should be created', inject([WorkflowActionService], (injectedService: WorkflowActionService) => {
    expect(injectedService).toBeTruthy();
  }));

  it('should add an operator to both jointjs and texera graph correctly', () => {
    service.addOperator(mockScanPredicate, mockPoint);

    expect(texeraGraph.hasOperator(mockScanPredicate.operatorID)).toBeTruthy();
    expect(jointGraph.getCell(mockScanPredicate.operatorID)).toBeTruthy();
  });

  it('should throw an error when adding an existed operator', () => {
    service.addOperator(mockScanPredicate, mockPoint);

    expect(() => {
      service.addOperator(mockScanPredicate, mockPoint);
    }).toThrowError(new RegExp(`exists`));
  });

  it('should throw an error when adding an operator with invalid operator type', () => {
    const invalidOperator: OperatorPredicate = {
      ...mockScanPredicate,
      operatorType: 'invalidOperatorTypeForTesting'
    };

    expect(() => {
      service.addOperator(invalidOperator, mockPoint);
    }).toThrowError(new RegExp(`invalid`));
  });

  it('should delete an operator to both jointjs and texera graph correctly', () => {
    service.addOperator(mockScanPredicate, mockPoint);

    service.deleteOperator(mockScanPredicate.operatorID);

    expect(texeraGraph.hasOperator(mockScanPredicate.operatorID)).toBeFalsy();
    expect(jointGraph.getCell(mockScanPredicate.operatorID)).toBeFalsy();
  });

  it('should throw an error when trying to delete an non-existing operator', () => {
    expect(() => {
      service.deleteOperator(mockScanPredicate.operatorID);
    }).toThrowError(new RegExp(`doesn't exist`));
  });


  it('should add a link to both jointjs and texera graph correctly', () => {
    service.addOperator(mockScanPredicate, mockPoint);
    service.addOperator(mockResultPredicate, mockPoint);

    service.addLink(mockScanResultLink);

    expect(texeraGraph.hasLink(mockScanResultLink.source, mockScanResultLink.target)).toBeTruthy();
    expect(texeraGraph.hasLinkWithID(mockScanResultLink.linkID)).toBeTruthy();
    expect(jointGraph.getCell(mockScanResultLink.linkID)).toBeTruthy();
  });

  it('should throw appropriate errors when adding various types of incorrect links', () => {
    service.addOperator(mockScanPredicate, mockPoint);
    service.addOperator(mockResultPredicate, mockPoint);
    service.addLink(mockScanResultLink);

    // link already exist
    expect(() => {
      service.addLink(mockScanResultLink);
    }).toThrowError(new RegExp('already exists'));

    const sameLinkDifferentID = {
      ...mockScanResultLink,
      linkID: 'link-2'
    };

    // same link but different id already exist
    expect(() => {
      service.addLink(sameLinkDifferentID);
    }).toThrowError(new RegExp('exists'));

    // link's target operator or port doesn't exist
    expect(() => {
      service.addLink(mockScanSentimentLink);
    }).toThrowError(new RegExp(`target .* doesn't exist`));

    // link's source operator or port doesn't exist
    expect(() => {
      service.addLink(mockSentimentResultLink);
    }).toThrowError(new RegExp(`source .* doesn't exist`));

    // add another operator for tests below
    texeraGraph.addOperator(mockSentimentPredicate);

    // link source portID doesn't exist (no output port for source operator)
    expect(() => {
      service.addLink(mockFalseResultSentimentLink);
    }).toThrowError(new RegExp(`on output ports of the source operator`));

    // link target portID doesn't exist (no input port for target operator)

    expect(() => {
      service.addLink(mockFalseSentimentScanLink);
    }).toThrowError(new RegExp(`on input ports of the target operator`));

  });

  it('should delete a link by link ID from both jointjs and texera graph correctly', () => {
    service.addOperator(mockScanPredicate, mockPoint);
    service.addOperator(mockResultPredicate, mockPoint);
    service.addLink(mockScanResultLink);

    // test delete by link ID
    service.deleteLinkWithID(mockScanResultLink.linkID);

    expect(texeraGraph.hasLink(mockScanResultLink.source, mockScanResultLink.target)).toBeFalsy();
    expect(texeraGraph.hasLinkWithID(mockScanResultLink.linkID)).toBeFalsy();
    expect(jointGraph.getCell(mockScanResultLink.linkID)).toBeFalsy();
  });

  it('should delete a link by source and target from both jointjs and texera graph correctly', () => {
    service.addOperator(mockScanPredicate, mockPoint);
    service.addOperator(mockResultPredicate, mockPoint);
    service.addLink(mockScanResultLink);

    // test delete by link source and target
    service.deleteLink(mockScanResultLink.source, mockScanResultLink.target);

    expect(texeraGraph.hasLink(mockScanResultLink.source, mockScanResultLink.target)).toBeFalsy();
    expect(texeraGraph.hasLinkWithID(mockScanResultLink.linkID)).toBeFalsy();
    expect(jointGraph.getCell(mockScanResultLink.linkID)).toBeFalsy();
  });

  it('should throw an error when trying to delete non-existing link', () => {
    service.addOperator(mockScanPredicate, mockPoint);
    service.addOperator(mockResultPredicate, mockPoint);

    expect(() => {
      service.deleteLinkWithID(mockScanResultLink.linkID);
    }).toThrowError(new RegExp(`doesn't exist`));

    expect(() => {
      service.deleteLinkWithID(mockScanResultLink.linkID);
    }).toThrowError(new RegExp(`doesn't exist`));
  });

  it('should set operator property to texera graph correctly', () => {
    service.addOperator(mockScanPredicate, mockPoint);

    const newProperty = { table: 'test-table' };
    service.setOperatorProperty(mockScanPredicate.operatorID, newProperty);

    const operator = texeraGraph.getOperator(mockScanPredicate.operatorID);
    if (! operator) {
      throw new Error(`operator ${mockScanPredicate.operatorID} doesn't exist`);
    }
    expect(operator.operatorProperties).toEqual(newProperty);
  });

  it('should throw an error when trying to set operator property of an nonexist operator', () => {
    expect(() => {
      const newProperty = { table: 'test-table' };
      service.setOperatorProperty(mockScanPredicate.operatorID, newProperty);
    }).toThrowError(new RegExp(`doesn't exist`));
  });

  it('should handle delete an operator causing connected links to be deleted correctly', () => {
    // add operator scan, sentiment, and result
    service.addOperator(mockScanPredicate, mockPoint);
    service.addOperator(mockSentimentPredicate, mockPoint);
    service.addOperator(mockResultPredicate, mockPoint);
    // add link scan -> result, and sentiment -> result
    service.addLink(mockScanResultLink);
    service.addLink(mockSentimentResultLink);

    // delete result operator, should cause two links to be deleted as well
    service.deleteOperator(mockResultPredicate.operatorID);

    expect(texeraGraph.getAllOperators().length).toEqual(2);
    expect(texeraGraph.getAllLinks().length).toEqual(0);

  });

});
