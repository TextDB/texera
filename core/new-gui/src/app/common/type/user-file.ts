/**
 * This interface stores the information about the users' files.
 * These information is used to locate the file for the operators.
 * Corresponds to `src/main/scala/edu/uci/ics/texera/web/resource/dashboard/file/UserFileResource.scala` (backend);
 * and `core/scripts/sql/texera_ddl.sql`, table `file` (database).
 */
//TODO: remove uid, fid, path from UserFile interface, they should not be accessible via front-end
export interface UserFile extends Readonly<{
  uid: number;
  fid: number;
  name: string;
  path: string;
  description: string;
  size: number;
}> {}

/**
 * This interface stores the information about the users' files when uploading.
 * These information is used to upload the file to the backend.
 */
export interface FileUploadItem {
  file: File;
  name: string;
  description: string;
  uploadProgress: number;
  isUploadingFlag: boolean;
}
