package com.gitrepository.gitrepository.dto;

import lombok.Data;

import java.util.Set;

@Data
public class ConsolidatedStatusDto {
private Set<String> untracked;
private Set<String> untrackedFolders;
private Set<String> added;
private Set<String> changed;
private Set<String> missing;
private Set<String> modified;
private Set<String> removed;
}
