CREATE TABLE IF NOT EXISTS kafka_clusters (
cluster_id BIGINT,
version VARCHAR(10),
scala_version VARCHAR(10),
configuration_params TEXT,
host_username VARCHAR(100),
encrypted_pkey TEXT,
hosts TEXT,
is_local BOOLEAN DEFAULT TRUE,
FOREIGN KEY (cluster_id) REFERENCES clusters(id) ON DELETE CASCADE
);

--Modify the cluster with the service details
ALTER TABLE clusters ADD COLUMN service_name VARCHAR(100);
ALTER TABLE clusters ADD COLUMN service_version VARCHAR(250);
ALTER TABLE clusters ADD COLUMN status_detail TEXT;

--Entries for the kafka package
INSERT INTO package_repository(name, version, is_deprecated) VALUES('kafka','2.7.2','false');
INSERT INTO package_repository(name, version, is_deprecated) VALUES('kafka','2.8.1','false');
INSERT INTO package_repository(name, version, is_deprecated) VALUES('kafka','3.0.0','false');