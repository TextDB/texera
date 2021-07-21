import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FormBuilder, Validators } from '@angular/forms';
import {
  UserWorkflowAccess,
  WorkflowGrantAccessService
} from '../../../../../common/service/user/workflow-access-control/workflow-grant-access.service';
import { Workflow } from '../../../../../common/type/workflow';
import {UserFileService} from "../../../../../common/service/user/user-file/user-file.service";
import {UserFile} from "../../../../../common/type/user-file";
import {UserFileAccess} from "../../../../../common/service/user/user-file/user-file.service";


@Component({
  selector: 'texera-ngbd-modal-file-share-access',
  templateUrl: './ngbd-modal-file-share-access.component.html',
  styleUrls: ['./ngbd-modal-file-share-access.component.scss']
})
export class NgbdModalFileShareAccessComponent implements OnInit {

  @Input() file!: UserFile;

  shareForm = this.formBuilder.group({
    username: ['',[Validators.required]],
    accessLevel: ['', [Validators.required]]
  });

  allUserFileAccess: Readonly<UserFileAccess>[] = [];

  accessLevels = ["read", "write"]


  public defaultWeb: String = 'http://localhost:4200/';

  constructor(
    public activeModal: NgbActiveModal,
    private userFileService: UserFileService,
    private formBuilder: FormBuilder
  ) {
  }

  ngOnInit(): void {
    this.refreshGrantedList(this.file);
  }


  public onClickGetAllSharedAccess(file: UserFile): void {
    this.refreshGrantedList(file);
  }

  /**
   * get all shared access of the current workflow
   * @param file target/current workflow
   */
  public refreshGrantedList(file: UserFile): void {
    this.userFileService.getSharedAccessesOfFile(file).subscribe(
      (userFileAccess: Readonly<UserFileAccess>[]) => this.allUserFileAccess = userFileAccess,
      err => console.log(err.error)
    );
  }

  /**
   * grant a specific level of access to a user
   * @param file the given/target workflow
   * @param userToShareWith the target user
   * @param accessLevel
   */
  public grantAccess(file: UserFile, userToShareWith: string, accessLevel: string): void {
    this.userFileService.grantAccess(file, userToShareWith, accessLevel).subscribe(
      () => this.refreshGrantedList(file),
      err => alert(err.error));
  }


  /**
   * triggered by clicking the SUBMIT button, offers access based on the input information
   * @param file target/current workflow
   */
  public onClickShareWorkflow(file: UserFile): void {
    if(this.shareForm.get('username')?.invalid){
      alert("Please Fill in Username")
      return
    }
    if(this.shareForm.get('accessLevel')?.invalid){
      alert("Please Select Access Level")
      return
    }
    const userToShareWith = this.shareForm.get('username')?.value;
    const accessLevel = this.shareForm.get('accessLevel')?.value;
    this.grantAccess(file, userToShareWith, accessLevel);
  }

  public onClickRemoveAccess(file: UserFile, userToRemove: string): void {
    this.userFileService.revokeFileAccess(file, userToRemove).subscribe(
      () => this.refreshGrantedList(file),
      err => alert(err.error)
    );
  }

  /**
   * change form information based on user behavior on UI
   * @param e selected value
   */
  changeType(e: any) {
    this.shareForm.setValue({'accessLevel': e.target.value});
  }


}
