CREATE SCHEMA IF NOT EXISTS `texera_db`;
USE `texera_db`;

DROP TABLE IF EXISTS `file`;
DROP TABLE IF EXISTS `keyword_dictionary`;
DROP TABLE IF EXISTS `workflow_of_user`;
DROP TABLE IF EXISTS `user_dictionary`;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS `workflow`;
DROP TABLE IF EXISTS `user_file_access`;
DROP TABLE IF EXISTS `workflow_user_access`;

SET GLOBAL time_zone = '+00:00'; # this line is mandatory

CREATE TABLE IF NOT EXISTS user
(
    `name`      VARCHAR(32)                 NOT NULL,
    `uid`       INT UNSIGNED AUTO_INCREMENT NOT NULL,
    `password`  VARCHAR(256),
    `google_id` VARCHAR(256) UNIQUE,
    PRIMARY KEY (`uid`),
    CONSTRAINT CK_nulltest
        CHECK (`password` IS NOT NULL OR `google_id` IS NOT NULL)
) ENGINE = INNODB,
-- start auto increment userID from 1 because userID 0 means user not exists
  AUTO_INCREMENT = 1;

CREATE TABLE IF NOT EXISTS user_dictionary
(
    `uid`   INT UNSIGNED NOT NULL,
    `key`   varchar(256) NOT NULL,
    `value` text         NOT NULL,
    PRIMARY KEY (`uid`, `key`),
    FOREIGN KEY (`uid`) REFERENCES user (`uid`) ON DELETE CASCADE
) ENGINE = InnoDB;


CREATE TABLE IF NOT EXISTS file
(
    `uid`         INT UNSIGNED                NOT NULL,
    `fid`         INT UNSIGNED AUTO_INCREMENT NOT NULL,
    `size`        INT UNSIGNED                NOT NULL,
    `name`        VARCHAR(128)                NOT NULL,
    `path`        VARCHAR(512)                NOT NULL,
    `description` VARCHAR(512)                NOT NULL,
    UNIQUE (`uid`, `name`),
    PRIMARY KEY (`fid`),
    FOREIGN KEY (`uid`) REFERENCES user (`uid`) ON DELETE CASCADE
) ENGINE = INNODB;

CREATE TABLE IF NOT EXISTS user_file_access(
    `uid`         INT UNSIGNED                NOT NULL,
    `fid`         INT UNSIGNED                NOT NULL,
    `read_access`  BIT(1),
    `write_access` BIT(1),
    PRIMARY KEY (`uid`, `fid`),
    FOREIGN KEY (`uid`) REFERENCES user (`uid`) ON DELETE CASCADE,
    FOREIGN KEY (`fid`) REFERENCES file (`fid`) ON DELETE CASCADE
)ENGINE = INNODB;

CREATE TABLE IF NOT EXISTS keyword_dictionary
(
    `uid`         INT UNSIGNED                NOT NULL,
    `kid`         INT UNSIGNED AUTO_INCREMENT NOT NULL,
    `name`        VARCHAR(128)                NOT NULL,
    `content`     MEDIUMBLOB                  NOT NULL,
    `description` VARCHAR(512)                NOT NULL,
    UNIQUE (`uid`, `name`),
    PRIMARY KEY (`kid`),
    FOREIGN KEY (`uid`) REFERENCES user (`uid`) ON DELETE CASCADE
) ENGINE = INNODB,
  AUTO_INCREMENT = 1;

CREATE TABLE IF NOT EXISTS workflow
(
    `name`               VARCHAR(128)                NOT NULL,
    `wid`                INT UNSIGNED AUTO_INCREMENT NOT NULL,
    `content`            TEXT                        NOT NULL,
    `creation_time`      TIMESTAMP                   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `last_modified_time` TIMESTAMP                   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`wid`)
) ENGINE = INNODB,
  AUTO_INCREMENT = 1;

CREATE TABLE IF NOT EXISTS workflow_of_user
(
    `uid` INT UNSIGNED NOT NULL,
    `wid` INT UNSIGNED NOT NULL,
    PRIMARY KEY (`uid`, `wid`),
    FOREIGN KEY (`uid`) REFERENCES `user` (`uid`) ON DELETE CASCADE,
    FOREIGN KEY (`wid`) REFERENCES `workflow` (`wid`) ON DELETE CASCADE
) ENGINE = INNODB;

CREATE TABLE IF NOT EXISTS workflow_user_access
(
    `uid` INT UNSIGNED NOT NULL,
    `wid` INT UNSIGNED NOT NULL,
    `read_privilege` BIT(1),
    `write_privilege` BIT(1),
    PRIMARY KEY (`uid`, `wid`),
    FOREIGN KEY (`uid`) REFERENCES `user` (`uid`) ON DELETE CASCADE,
    FOREIGN KEY (`wid`) REFERENCES `workflow` (`wid`) ON DELETE CASCADE
) ENGINE = INNODB;
