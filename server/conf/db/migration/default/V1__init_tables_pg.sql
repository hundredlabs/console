CREATE TABLE IF NOT EXISTS members (
id SERIAL PRIMARY KEY,
 name VARCHAR(100),
 email VARCHAR(100),
 member_type CHAR(10),
 receive_updates BOOLEAN DEFAULT false,
 activated BOOLEAN DEFAULT false,
 dt_joined DATE
 );

CREATE TABLE IF NOT EXISTS member_api_keys(
   id BIGSERIAL PRIMARY KEY,
   name VARCHAR(100) ,
   api_key VARCHAR(500) UNIQUE,
   encrypted_api_secret_key varchar(1000),
   member_id BIGINT,
   created TIMESTAMP(3),
   last_used TIMESTAMP(3),
   FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
);
 
 CREATE TABLE IF NOT EXISTS orgs (
 id SERIAL PRIMARY KEY,
  name VARCHAR(100),
  owner INT,
  thumbnail_img VARCHAR(1000),
  slug_id VARCHAR(100),
  dt_created DATE,
  FOREIGN KEY (owner) REFERENCES members(id) ON DELETE CASCADE
  );

  CREATE TABLE login_info(
  id SERIAL PRIMARY KEY,
  provider_id VARCHAR(100),
  provider_key VARCHAR(500)
  );
  
CREATE TABLE credentials(
id SERIAL PRIMARY KEY,
email VARCHAR(100),
hasher VARCHAR(100),
password VARCHAR(500),
salt VARCHAR(100),
login_info_id INT,
FOREIGN KEY (login_info_id) REFERENCES login_info(id) ON DELETE CASCADE
);

CREATE TABLE user_login_info(
member_id INT,
login_info_id INT,
login_token VARCHAR(200),
FOREIGN KEY (login_info_id) REFERENCES login_info(id) ON DELETE CASCADE
);

CREATE TABLE oauth1_info(
id BIGSERIAL PRIMARY KEY,
token VARCHAR(1000),
secret VARCHAR(1000),
login_info_id INT,
FOREIGN KEY (login_info_id) REFERENCES login_info(id) ON DELETE CASCADE
);

CREATE TABLE oauth2_info(
id BIGSERIAL PRIMARY KEY,
access_token VARCHAR(1000),
token_type VARCHAR(1000),
expires_in INT,
refresh_token VARCHAR(1000),
login_info_id INT,
FOREIGN KEY (login_info_id) REFERENCES login_info(id) ON DELETE CASCADE
);

CREATE TABLE auth_token(
id varchar(200) PRIMARY KEY,
member_id BIGINT,
expiry TIMESTAMP(3)
);


CREATE TABLE IF NOT EXISTS workspaces(
id BIGSERIAL PRIMARY KEY,
org_id INT,
name VARCHAR(150),
description TEXT,
dt_created TIMESTAMP(3),
created_by INT,
pkey_version VARCHAR(100),
thumbnail_img VARCHAR(1000),
FOREIGN KEY (org_id) REFERENCES orgs(id) ON DELETE CASCADE,
FOREIGN KEY (created_by) REFERENCES members(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS clusters(
id BIGSERIAL PRIMARY KEY,
name VARCHAR(1000),
provider VARCHAR(50),
provider_cluster_id VARCHAR(500),
status VARCHAR(50),
region VARCHAR(20),
extra_services varchar(500),
service_version VARCHAR(250),
service_name VARCHAR(100),
status_detail TEXT,
workspace_id BIGINT,
sandbox_config JSON,
dt_added TIMESTAMP(3),
last_updated TIMESTAMP(3),
FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE
);


CREATE TABLE workspace_crypto_keypairs(
hex_pub_key VARCHAR(1000),
hex_private_key VARCHAR(2000),
workspace_id BIGINT,
FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE
);

--Roles defined for all the organization
--org.admin = Can add members, roles, workspaces and generate usage reports
--org.billing = View and generate billing reports
--workspace.manager = Add accounts, delete workspace, create and manange teams
--workspace.member = Add clusters, deploy applications to clusters, monitor app performance, create alerts
--workspace.viewer = View clusters, jobs and subscribe to alerts
CREATE TABLE IF NOT EXISTS roles(
id SERIAL PRIMARY KEY,
name VARCHAR(100),
access_policies TEXT,
manager_id BIGINT,
FOREIGN KEY (manager_id) REFERENCES orgs(id) ON DELETE CASCADE
);

--Defines the member roles
CREATE TABLE IF NOT EXISTS member_roles(
member_id BIGINT,
role_id INT,
subject_type VARCHAR(15),
subject_id BIGINT,
FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE,
FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);
CREATE INDEX member_roles_member_id_idx ON member_roles (member_id);

CREATE TABLE IF NOT EXISTS member_profile(
member_id BIGINT PRIMARY KEY,
current_org_id BIGINT,
current_workspace_id BIGINT,
web_theme varchar(10),
desktop_theme varchar(10),
FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE cascade,
FOREIGN KEY (current_org_id) REFERENCES orgs(id) ON DELETE cascade,
FOREIGN KEY (current_workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS workspace_api_keys(
id SERIAL PRIMARY KEY,
name varchar(500),
api_key varchar(100),
encrypted_api_secret_key TEXT,
workspace_id BIGINT,
FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE cascade
);

CREATE TABLE global_secret(
hex_public_key TEXT,
hex_private_key TEXT);


CREATE TABLE IF NOT EXISTS org_integrations(
id SERIAL PRIMARY KEY,
hex_pub_key VARCHAR(1000),
hex_private_key TEXT,
integration_type VARCHAR(100),
org_id BIGINT,
FOREIGN KEY (org_id) REFERENCES orgs(id) ON DELETE cascade
);

CREATE TABLE IF NOT EXISTS sandbox_cluster(
id BIGSERIAL,
version VARCHAR(10),
service_options JSON
);

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
