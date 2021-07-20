import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SchemaPropagationService } from '../../../service/dynamic-schema/schema-propagation/schema-propagation.service';

import { TypeCastingDisplayComponent } from './type-casting-display.component';
import { WorkflowActionService } from '../../../service/workflow-graph/model/workflow-action.service';
import { OperatorMetadataService } from '../../../service/operator-metadata/operator-metadata.service';
import { StubOperatorMetadataService } from '../../../service/operator-metadata/stub-operator-metadata.service';
import { JointUIService } from '../../../service/joint-ui/joint-ui.service';
import { UndoRedoService } from '../../../service/undo-redo/undo-redo.service';
import { WorkflowUtilService } from '../../../service/workflow-graph/util/workflow-util.service';
import { LoggerModule } from 'ngx-logger';

describe('TypecastingDisplayComponent', () => {
  let component: TypeCastingDisplayComponent;
  let fixture: ComponentFixture<TypeCastingDisplayComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      imports: [
        HttpClientTestingModule,
        LoggerModule.forRoot(undefined)
      ],
      providers: [
        { provide: OperatorMetadataService, useClass: StubOperatorMetadataService },
        JointUIService,
        UndoRedoService,
        WorkflowUtilService,
        WorkflowActionService,
        SchemaPropagationService,
      ],
      declarations: [ TypeCastingDisplayComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(TypeCastingDisplayComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
