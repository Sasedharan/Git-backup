package com.gitrepository.gitrepository.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommitDto {
private String commitId;
private String author;
private String date;
private String message;
}
