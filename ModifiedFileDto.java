package com.gitrepository.gitrepository.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ModifiedFileDto {
  private String fileName;
  private String changes;
  private String fileUrl;
}

