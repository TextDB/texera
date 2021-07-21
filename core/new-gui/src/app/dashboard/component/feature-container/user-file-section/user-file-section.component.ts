import { Component, OnInit } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { NgbdModalFileAddComponent } from './ngbd-modal-file-add/ngbd-modal-file-add.component';
import { UserFileService } from '../../../../common/service/user/user-file/user-file.service';
import { UserFile } from '../../../../common/type/user-file';
import { UserService } from '../../../../common/service/user/user.service';
import {NgbdModalFileShareAccessComponent} from "./ngbd-modal-file-share-access/ngbd-modal-file-share-access.component";

@Component({
  selector: 'texera-user-file-section',
  templateUrl: './user-file-section.component.html',
  styleUrls: ['./user-file-section.component.scss']
})
export class UserFileSectionComponent implements OnInit {

  constructor(
    private modalService: NgbModal,
    private userFileService: UserFileService,
    private userService: UserService
  ) {
    this.userFileService.refreshFiles();
  }

  ngOnInit() {
  }

  public openFileAddComponent() {
    this.modalService.open(NgbdModalFileAddComponent);
  }

  public onClickOpenShareAccess(userFile: UserFile): void{
    const modalRef = this.modalService.open(NgbdModalFileShareAccessComponent);
    modalRef.componentInstance.file = userFile;
  }

  public getFileArray(): ReadonlyArray<UserFile> {
    const fileArray = this.userFileService.getUserFiles();
    if (!fileArray) {
      return [];
    }
    return fileArray;
  }

  public deleteFile(userFile: UserFile): void {
    this.userFileService.deleteFile(userFile);
  }

  public disableAddButton(): boolean {
    return !this.userService.isLogin();
  }

  public addFileSizeUnit(fileSize: number): string {
    return this.userFileService.addFileSizeUnit(fileSize);
  }

}
