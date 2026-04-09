package com.example.k8soperator.staticsite;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StaticSiteDeployerSpec {

  private String gitRepoUrl;
  private String branch;
  private String nginxImage;

  public String getGitRepoUrl() {
    return gitRepoUrl;
  }

  public void setGitRepoUrl(String gitRepoUrl) {
    this.gitRepoUrl = gitRepoUrl;
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public String getNginxImage() {
    return nginxImage;
  }

  public void setNginxImage(String nginxImage) {
    this.nginxImage = nginxImage;
  }
}
