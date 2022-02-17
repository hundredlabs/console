CREATE TABLE IF NOT EXISTS hadoop_clusters (
cluster_id BIGINT,
version VARCHAR(10),
core_site TEXT,
hdfs_site TEXT,
host_username VARCHAR(100),
encrypted_pkey TEXT,
hosts TEXT,
is_local BOOLEAN DEFAULT TRUE,
FOREIGN KEY (cluster_id) REFERENCES clusters(id) ON DELETE CASCADE
);

--Entries for the hadoop packages
INSERT INTO package_repository(name, version, is_deprecated) VALUES('hadoop','3.3.1','false');
INSERT INTO package_repository(name, version, is_deprecated) VALUES('hadoop','3.2.2','false');
INSERT INTO package_repository(name, version, is_deprecated) VALUES('hadoop','2.10.1','false');