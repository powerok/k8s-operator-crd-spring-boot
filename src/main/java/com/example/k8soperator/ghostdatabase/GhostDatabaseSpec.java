package com.example.k8soperator.ghostdatabase;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GhostDatabaseSpec {

  /** Logical database name (POSTGRES_DB). */
  private String databaseName;

  /** PVC size, e.g. 1Gi. */
  private String storageSize;

  /** Optional image override (default postgres:16-alpine). */
  private String postgresImage;

  /** Optional SQL run on first init (mounted as /docker-entrypoint-initdb.d/init.sql). */
  private String initSql;

  public String getDatabaseName() {
    return databaseName;
  }

  public void setDatabaseName(String databaseName) {
    this.databaseName = databaseName;
  }

  public String getStorageSize() {
    return storageSize;
  }

  public void setStorageSize(String storageSize) {
    this.storageSize = storageSize;
  }

  public String getPostgresImage() {
    return postgresImage;
  }

  public void setPostgresImage(String postgresImage) {
    this.postgresImage = postgresImage;
  }

  public String getInitSql() {
    return initSql;
  }

  public void setInitSql(String initSql) {
    this.initSql = initSql;
  }
}
