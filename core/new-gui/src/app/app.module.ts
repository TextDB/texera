import { registerLocaleData } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import en from '@angular/common/locales/en';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { RouterModule } from '@angular/router';
import { NgbModule, NgbPopoverModule } from '@ng-bootstrap/ng-bootstrap';
import { FormlyModule } from '@ngx-formly/core';
import { FormlyMaterialModule } from '@ngx-formly/material';
import { FormlyMatDatepickerModule } from '@ngx-formly/material/datepicker';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCollapseModule } from 'ng-zorro-antd/collapse';
import { NzDatePickerModule } from 'ng-zorro-antd/date-picker';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzFormModule } from 'ng-zorro-antd/form';
import { en_US, NZ_I18N } from 'ng-zorro-antd/i18n';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzListModule } from 'ng-zorro-antd/list';
import { NzMenuModule } from 'ng-zorro-antd/menu';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSliderModule } from 'ng-zorro-antd/slider';
import { NzSpaceModule } from 'ng-zorro-antd/space';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { FileUploadModule } from 'ng2-file-upload';
import { NgxAceModule } from 'ngx-ace-icy';
import { NgxJsonViewerModule } from 'ngx-json-viewer';
import { LoggerModule, NgxLoggerLevel } from 'ngx-logger';
import { TourNgBootstrapModule } from 'ngx-tour-ng-bootstrap';
import { environment } from '../environments/environment';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { CustomNgMaterialModule } from './common/custom-ng-material.module';
import { ArrayTypeComponent } from './common/formly/array.type';
import { TEXERA_FORMLY_CONFIG } from './common/formly/formly-config';
import { MultiSchemaTypeComponent } from './common/formly/multischema.type';
import { NullTypeComponent } from './common/formly/null.type';
import { ObjectTypeComponent } from './common/formly/object.type';
import { UserDictionaryUploadService } from './common/service/user/user-dictionary/user-dictionary-upload.service';
import { UserDictionaryService } from './common/service/user/user-dictionary/user-dictionary.service';
import { UserFileUploadService } from './common/service/user/user-file/user-file-upload.service';
import { UserFileService } from './common/service/user/user-file/user-file.service';
import { UserService } from './common/service/user/user.service';
import { DashboardComponent } from './dashboard/component/dashboard.component';
import { FeatureBarComponent } from './dashboard/component/feature-bar/feature-bar.component';
import { FeatureContainerComponent } from './dashboard/component/feature-container/feature-container.component';
import { ResourceSectionComponent } from './dashboard/component/feature-container/resource-section/resource-section.component';
import { RunningJobSectionComponent } from './dashboard/component/feature-container/running-job-section/running-job-section.component';
import { NgbdModalAddWorkflowComponent } from './dashboard/component/feature-container/saved-workflow-section/ngbd-modal-add-workflow/ngbd-modal-add-workflow.component';
import { NgbdModalDeleteWorkflowComponent } from './dashboard/component/feature-container/saved-workflow-section/ngbd-modal-delete-workflow/ngbd-modal-delete-workflow.component';
import { SavedWorkflowSectionComponent } from './dashboard/component/feature-container/saved-workflow-section/saved-workflow-section.component';
import { NgbdModalResourceAddComponent } from './dashboard/component/feature-container/user-dictionary-section/ngbd-modal-resource-add/ngbd-modal-resource-add.component';
import { NgbdModalResourceDeleteComponent } from './dashboard/component/feature-container/user-dictionary-section/ngbd-modal-resource-delete/ngbd-modal-resource-delete.component';
import { NgbdModalResourceViewComponent } from './dashboard/component/feature-container/user-dictionary-section/ngbd-modal-resource-view/ngbd-modal-resource-view.component';
import { UserDictionarySectionComponent } from './dashboard/component/feature-container/user-dictionary-section/user-dictionary-section.component';
import { NgbdModalFileAddComponent } from './dashboard/component/feature-container/user-file-section/ngbd-modal-file-add/ngbd-modal-file-add.component';
import { UserFileSectionComponent } from './dashboard/component/feature-container/user-file-section/user-file-section.component';
import { TopBarComponent } from './dashboard/component/top-bar/top-bar.component';
import { UserIconComponent } from './dashboard/component/top-bar/user-icon/user-icon.component';
import { NgbdModalUserLoginComponent } from './dashboard/component/top-bar/user-icon/user-login/ngbdmodal-user-login.component';
import { CodeEditorDialogComponent } from './workspace/component/code-editor-dialog/code-editor-dialog.component';
import { CodeareaCustomTemplateComponent } from './workspace/component/codearea-custom-template/codearea-custom-template.component';
import { MiniMapComponent } from './workspace/component/workflow-editor/mini-map/mini-map.component';
import { NavigationComponent } from './workspace/component/navigation/navigation.component';
import { OperatorLabelComponent } from './workspace/component/operator-panel/operator-label/operator-label.component';
import { OperatorPanelComponent } from './workspace/component/operator-panel/operator-panel.component';
import { ProductTourComponent } from './workspace/component/product-tour/product-tour.component';
import { PropertyEditorComponent } from './workspace/component/property-editor/property-editor.component';
import { TypecastingDisplayComponent } from './workspace/component/property-editor/typecasting-display/typecasting-display.component';
import { ResultPanelToggleComponent } from './workspace/component/result-panel-toggle/result-panel-toggle.component';
import { ResultPanelComponent, RowModalComponent } from './workspace/component/result-panel/result-panel.component';
import { VisualizationPanelContentComponent } from './workspace/component/visualization-panel-content/visualization-panel-content.component';
import { VisualizationPanelComponent } from './workspace/component/visualization-panel/visualization-panel.component';
import { WorkflowEditorComponent } from './workspace/component/workflow-editor/workflow-editor.component';
import { WorkspaceComponent } from './workspace/component/workspace.component';
import { ResultDownloadComponent } from './workspace/component/navigation/result-download/result-download.component';
import { GoogleApiModule, NG_GAPI_CONFIG } from 'ng-gapi';
import {NgbdModalShareAccessComponent} from './dashboard/component/feature-container/saved-workflow-section/ngbd-modal-share-access/ngbd-modal-share-access.component';
import {NgbdModalFileShareAccessComponent} from "./dashboard/component/feature-container/user-file-section/ngbd-modal-file-share-access/ngbd-modal-file-share-access.component";

registerLocaleData(en);

@NgModule({
  declarations: [
    AppComponent,
    WorkspaceComponent,
    NavigationComponent,
    OperatorPanelComponent,
    PropertyEditorComponent,
    WorkflowEditorComponent,
    ResultPanelComponent,
    OperatorLabelComponent,
    DashboardComponent,
    TopBarComponent,
    UserIconComponent,
    FeatureBarComponent,
    FeatureContainerComponent,
    SavedWorkflowSectionComponent,
    NgbdModalAddWorkflowComponent,
    NgbdModalDeleteWorkflowComponent,
    RunningJobSectionComponent,
    UserDictionarySectionComponent,
    NgbdModalResourceViewComponent,
    NgbdModalResourceAddComponent,
    NgbdModalResourceDeleteComponent,
    NgbdModalUserLoginComponent,
    UserFileSectionComponent,
    NgbdModalFileAddComponent,
    ResourceSectionComponent,
    RowModalComponent,
    OperatorLabelComponent,
    ProductTourComponent,
    MiniMapComponent,
    ResultPanelToggleComponent,
    ArrayTypeComponent,
    ObjectTypeComponent,
    MultiSchemaTypeComponent,
    NullTypeComponent,
    VisualizationPanelComponent,
    VisualizationPanelContentComponent,
    CodeareaCustomTemplateComponent,
    CodeEditorDialogComponent,
    TypecastingDisplayComponent,
    ResultDownloadComponent,
    NgbdModalShareAccessComponent,
    NgbdModalFileShareAccessComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
    MatTooltipModule,
    NgxJsonViewerModule,
    CustomNgMaterialModule,
    BrowserAnimationsModule,
    NgbModule,
    NgbPopoverModule,
    RouterModule.forRoot([]),
    TourNgBootstrapModule.forRoot(),
    FileUploadModule,
    FormsModule,
    ReactiveFormsModule,
    LoggerModule.forRoot({
      level: environment.production ? NgxLoggerLevel.ERROR :
        NgxLoggerLevel.DEBUG, serverLogLevel: NgxLoggerLevel.OFF
    }),
    FormlyModule.forRoot(TEXERA_FORMLY_CONFIG),
    FormlyMaterialModule,
    FormlyMatDatepickerModule,
    GoogleApiModule.forRoot({
      provide: NG_GAPI_CONFIG,
      useValue: {
        client_id: environment.google.clientID
      }
    }),
    NzDatePickerModule,
    NzDropDownModule,
    NzButtonModule,
    NzIconModule,
    NzFormModule,
    NzListModule,
    NzInputModule,
    NzMenuModule,
    NzCollapseModule,
    NzToolTipModule,
    NzTableModule,
    NzModalModule,
    NzSelectModule,
    NzSliderModule,
    NzSpaceModule,
    NzBadgeModule,
    NgxAceModule,
    MatDialogModule
  ],
  entryComponents: [
    NgbdModalAddWorkflowComponent,
    NgbdModalDeleteWorkflowComponent,
    NgbdModalResourceViewComponent,
    NgbdModalResourceAddComponent,
    NgbdModalResourceDeleteComponent,
    NgbdModalUserLoginComponent,
    RowModalComponent,
    NgbdModalFileAddComponent,
    NgbdModalShareAccessComponent
  ],
  providers: [
    HttpClientModule,
    UserService,
    UserFileService,
    UserFileUploadService,
    UserDictionaryService,
    UserDictionaryUploadService,
    {provide: NZ_I18N, useValue: en_US}
  ],
  bootstrap: [AppComponent]
  // dynamically created component must be placed in the entryComponents attribute
})
export class AppModule {
}
