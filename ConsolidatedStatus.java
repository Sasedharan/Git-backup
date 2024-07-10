package com.gitrepository.gitrepository.dto;

import java.util.LinkedHashSet;
import java.util.Set;

import lombok.Data;
import org.eclipse.jgit.api.Status;

@Data
public class ConsolidatedStatus {
private Set<String> untracked = new LinkedHashSet<>();
private Set<String> untrackedFolders = new LinkedHashSet<>();
private Set<String> added = new LinkedHashSet<>();
private Set<String> changed = new LinkedHashSet<>();
private Set<String> missing = new LinkedHashSet<>();
private Set<String> modified = new LinkedHashSet<>();
private Set<String> removed = new LinkedHashSet<>();

public void addStatus(Status status) {
untracked.addAll(status.getUntracked());
untrackedFolders.addAll(status.getUntrackedFolders());
added.addAll(status.getAdded());
changed.addAll(status.getChanged());
missing.addAll(status.getMissing());
modified.addAll(status.getModified());
removed.addAll(status.getRemoved());
}
}
