package com.gitrepository.gitrepository.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileStructureResponse {
private String name;
private boolean isDirectory;
}
