import { async, ComponentFixture, inject, TestBed } from '@angular/core/testing';

import { NgbModal, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { CustomNgMaterialModule } from '../../../../common/custom-ng-material.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatListModule } from '@angular/material/list';
import {NgbModalRef} from "@ng-bootstrap/ng-bootstrap";
import { UserFileSectionComponent } from './user-file-section.component';
import { UserFileService } from '../../../../common/service/user/user-file/user-file.service';
import { UserService } from '../../../../common/service/user/user.service';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { StubUserService } from '../../../../common/service/user/stub-user.service';
import {UserFile} from "../../../../common/type/user-file";
import {NgbdModalFileShareAccessComponent} from "./ngbd-modal-file-share-access/ngbd-modal-file-share-access.component";
import {NgbdModalShareAccessComponent} from "../saved-workflow-section/ngbd-modal-share-access/ngbd-modal-share-access.component";

describe('UserFileSectionComponent', () => {
  let component: UserFileSectionComponent;
  let fixture: ComponentFixture<UserFileSectionComponent>;
  let modalService: NgbModal;
  let modalRef: NgbModalRef;

  const id = 1;
  const name = 'testFile';
  const path = 'test/path';
  const description = 'this is a test file';
  const size = 1024;
  const testFile: UserFile = {
    uid: id,
    fid: id,
    name: name,
    path: path,
    size: size,
    description: description
  };
  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [UserFileSectionComponent],
      providers: [
        NgbModal,
        {provide: UserService, useClass: StubUserService},
        UserFileService
      ],
      imports: [
        CustomNgMaterialModule,
        NgbModule,
        FormsModule,
        ReactiveFormsModule,
        MatListModule,
        HttpClientTestingModule
      ]
    })
      .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(UserFileSectionComponent);
    component = fixture.componentInstance;
    modalService = TestBed.get(NgbModal);
    modalRef = modalService.open(NgbdModalFileShareAccessComponent);
    spyOn(modalService, 'open').and.returnValue(modalRef);
    fixture.detectChanges();
  });

  it('Modal Opened', () => {
    component.onClickOpenShareAccess(testFile);
    expect(modalService.open).toHaveBeenCalled();
  });

  it('should create', inject([HttpTestingController],
    (httpMock: HttpTestingController) => {
      expect(component).toBeTruthy();
    }));
});
