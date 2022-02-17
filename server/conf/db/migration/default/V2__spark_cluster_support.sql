-- Table for storing all the metadata regarding the clusters
CREATE TABLE IF NOT EXISTS spark_clusters (
cluster_id BIGINT,
version VARCHAR(10),
scala_version VARCHAR(10),
configuration_params TEXT,
host_username VARCHAR(100),
encrypted_pkey TEXT,
hosts TEXT,
cluster_manager VARCHAR(20),
is_local BOOLEAN DEFAULT TRUE,
FOREIGN KEY (cluster_id) REFERENCES clusters(id) ON DELETE CASCADE
);


-- Table for storing all the metadata regarding the cluster processes
CREATE TABLE IF NOT EXISTS cluster_processes (
cluster_id BIGINT,
name VARCHAR(100),
host_ip VARCHAR(100),
port VARCHAR(10),
status VARCHAR(20),
pid INT,
FOREIGN KEY (cluster_id) REFERENCES clusters(id) ON DELETE CASCADE
);

-- Rename the column of workspace_crypto_keypairs
ALTER TABLE workspace_crypto_keypairs rename column encrypted_pkey TO hex_private_key;

CREATE TABLE IF NOT EXISTS server_hosts (
id BIGSERIAL,
name VARCHAR(500),
host_ip VARCHAR(100),
cluster_id BIGINT,
status VARCHAR(20),
dt_added TIMESTAMP,
FOREIGN KEY (cluster_id) REFERENCES clusters(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS package_repository (
id BIGSERIAL,
name VARCHAR(500),
version VARCHAR(20),
is_deprecated BOOLEAN
);


