package com.gitrepository.gitrepository.dto;

//public class FileDto {
//    private String fileName;
//    public FileDto(String fileName) {
//        this.fileName = fileName;
//    }
//    public String getFileName() {
//        return fileName;
//    }
//    public void setFileName(String fileName) {
//        this.fileName = fileName;
//    }
//}


public class FileDto {
    private String fileName;
    private String fileContent;

    public FileDto(String fileName, String fileContent) {
        this.fileName = fileName;
        this.fileContent = fileContent;
    }

    public String getFileContent() {
        return fileContent;
    }

    public void setFileContent(String fileContent) {
        this.fileContent = fileContent;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}

