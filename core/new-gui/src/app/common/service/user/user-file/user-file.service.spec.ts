import { TestBed } from '@angular/core/testing';

import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { UserFile } from '../../../type/user-file';

import {
  UserFileService,
  USER_FILE_LIST_URL,
  USER_FILE_SHARE_ACCESS_URL,
  USER_REVOKE_ACCESS_URL, USER_FILE_ACCESS_LIST_URL
} from './user-file.service';
import { UserService } from '../user.service';
import { StubUserService, MOCK_USER } from '../stub-user.service';
import { AppSettings } from 'src/app/common/app-setting';
import {WORKFLOW_ACCESS_GRANT_URL} from "../workflow-access-control/workflow-grant-access.service";

const username = "Jim";
const accessLevel = "read";
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

describe('UserFileService', () => {
  let httpMock: HttpTestingController;
  let service: UserFileService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        UserFileService,
        { provide: UserService, useClass: StubUserService }
      ],
      imports: [
        HttpClientTestingModule
      ]
    });
    httpMock = TestBed.get(HttpTestingController);
    service = TestBed.get(UserFileService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });


  it('can share access', () => {
    service.grantAccess(testFile, username, accessLevel).first().subscribe();
    const req = httpMock.expectOne(
      `${AppSettings.getApiEndpoint()}/${USER_FILE_SHARE_ACCESS_URL}/${testFile.fid}/${username}/${accessLevel}`);
    expect(req.request.method).toEqual('POST');
    req.flush({code: 0, message: ''});
  })

  it('can revoke access', () => {
    service.revokeFileAccess(testFile, username).first().subscribe();
    const req = httpMock.expectOne(
      `${AppSettings.getApiEndpoint()}/${USER_REVOKE_ACCESS_URL}/${testFile.fid}/${username}`);
    expect(req.request.method).toEqual('POST');
    req.flush({code: 0, message: ''});
  })

  it('can get all access', () => {
    service.getSharedAccessesOfFile(testFile).first().subscribe();
    const req = httpMock.expectOne(
      `${AppSettings.getApiEndpoint()}/${USER_FILE_ACCESS_LIST_URL}/${testFile.fid}`);
    expect(req.request.method).toEqual('GET');
    req.flush({code: 0, message: ''});
  })

});
