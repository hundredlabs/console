CREATE TABLE IF NOT EXISTS approved_requests (
req_id SERIAL PRIMARY KEY,
email VARCHAR(100),
activation_code VARCHAR(200),
activation_status BOOLEAN DEFAULT false,
dt_approved DATE NOT NULL DEFAULT CURRENT_DATE
);


CREATE TABLE IF NOT EXISTS alpha_requests (
req_id SERIAL PRIMARY KEY,
email VARCHAR(100),
code_sent BOOLEAN DEFAULT false,
dt_requested DATE
);

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

CREATE TABLE job_run_stats(
run_id INT PRIMARY KEY,
job_id INT,
cluster_mem BIGINT,
cluster_mem_used NUMERIC(16,1),
avg_cpu_usage  NUMERIC(16,2),
avg_memory_usage  NUMERIC(16,2),
runtime INT,
status varchar(25)
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

CREATE TABLE IF NOT EXISTS jobs(
 id SERIAL PRIMARY KEY,
 name VARCHAR(200),
 description VARCHAR(500),
 workspace_id BIGINT,
 job_type VARCHAR(100),
 dt_created TIMESTAMP(3),
 last_updated timestamp(3),
 FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS job_history(
id SERIAL PRIMARY KEY,
 job_id INT,
 status VARCHAR(20),
 run_index INT,
 deployment_run_id int,
 action_id varchar(500),
 soft_deleted BOOLEAN DEFAULT FALSE,
 dt_started timestamp(3),
 last_updated timestamp(3),
 FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE);
 
 CREATE TABLE IF NOT EXISTS spark_project_config(
 project_id INT,
 interval_progress_events INT,
 interval_metrics INT,
 interval_log_events INT,
 FOREIGN KEY (project_id) REFERENCES jobs(id) ON DELETE CASCADE
);
 
CREATE TABLE IF NOT EXISTS project_stats(
 project_id INT PRIMARY KEY,
 avg_runtime FLOAT(4),
 avg_memory_usage FLOAT(4),
 avg_cpu_usage FLOAT(4),
 FOREIGN KEY (project_id) REFERENCES jobs(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS clusters(
id BIGSERIAL PRIMARY KEY,
name VARCHAR(1000),
provider VARCHAR(50),
provider_cluster_id VARCHAR(500),
status VARCHAR(50),
region VARCHAR(20),
extra_services varchar(500),
workspace_id BIGINT,
sandbox_config JSON,
dt_added TIMESTAMP(3),
last_updated TIMESTAMP(3),
FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS deployment_config(
id SERIAL PRIMARY KEY,
job_id INT,
name VARCHAR(250),
target_id BIGINT,
config JSON,
dt_created TIMESTAMP(3),
FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
FOREIGN KEY (target_id) REFERENCES clusters(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS deployment_history(
id BIGSERIAL PRIMARY KEY,
deployment_id INT,
run_index int,
trigger_method VARCHAR(20),
status VARCHAR(20),
dt_last_updated TIMESTAMP(3),
dt_started TIMESTAMP(3),
FOREIGN KEY (deployment_id) REFERENCES deployment_config(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS events_spark_app(
dep_run_id BIGINT,
app_id VARCHAR(100) NOT NULL,
spark_version VARCHAR(20),
master VARCHAR(100),
executor_mem VARCHAR(20),
driver_mem VARCHAR(20),
executor_cores INT,
memory_overhead VARCHAR(20),
app_name VARCHAR(200) NOT NULL,
spark_user VARCHAR(100) NOT NULL,
user_timezone VARCHAR(100) DEFAULT 'Asia/Kolkata',
app_attempt_id VARCHAR(150),
app_status VARCHAR(20),
total_executor_runtime BIGINT,
total_gc_time BIGINT,
avg_task_runtime INT,
max_task_runtime INT,
last_updated TIMESTAMP(3),
start_time TIMESTAMP(3),
end_time TIMESTAMP(3),
PRIMARY KEY(dep_run_id, app_id, app_attempt_id),
FOREIGN KEY (dep_run_id) REFERENCES deployment_history(id) ON DELETE CASCADE
);
 
CREATE TABLE IF NOT EXISTS events_spark_jobs(
name VARCHAR(500),
dep_run_id BIGINT,
job_id INT,
app_id VARCHAR(100),
app_attempt_id VARCHAR(150),
job_status VARCHAR(20),
num_stages INT,
num_stages_completed INT,
failed_reason TEXT,
start_time TIMESTAMP(3),
end_time TIMESTAMP(3),
PRIMARY KEY(job_id,dep_run_id),
FOREIGN KEY (dep_run_id) REFERENCES deployment_history(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS events_spark_stage(
dep_run_id BIGINT,
parent_job_id INT,
stage_id INT,
name VARCHAR(500),
app_attempt_id VARCHAR(150),
bytes_read bigint NOT NULL DEFAULT 0,
records_read bigint NOT NULL DEFAULT 0,
bytes_written bigint NOT NULL DEFAULT 0,
records_written bigint NOT NULL DEFAULT 0,
stage_attempt_id INT,
stage_status VARCHAR(20),
num_tasks INT,
num_tasks_completed INT,
num_tasks_failed INT,
scheduler_delay INT,
executor_compute_time INT,
shuffle_read_time INT,
shuffle_write_time INT,
getting_result_time INT,
task_deserialization_time INT,
result_deserialization_time INT,
input_read_records_min bigint,
input_read_records_max bigint,
input_read_records_mean NUMERIC(16,2),
input_read_records_total bigint,
input_read_size_min bigint,
input_read_size_max bigint,
input_read_size_mean NUMERIC(16,2),
input_read_size_total  bigint,
output_write_size_min bigint,
output_write_size_max bigint,
output_write_size_mean NUMERIC(16,2),
output_write_size_total bigint,
output_write_records_min bigint,
output_write_records_max bigint,
output_write_records_mean NUMERIC(16,2),
output_write_records_total bigint,
task_duration_min INT,
task_duration_max INT,
task_duration_mean NUMERIC(10,2),
gc_time_min INT,
gc_time_max INT,
gc_time_mean NUMERIC(10,1),
shuffle_read_records_min bigint,
shuffle_read_records_max bigint,
shuffle_read_records_mean NUMERIC(16,1),
shuffle_read_size_min bigint,
shuffle_read_size_max bigint,
shuffle_read_size_mean NUMERIC(16,1),
shuffle_write_size_min bigint,
shuffle_write_size_max bigint,
shuffle_write_size_mean NUMERIC(16,1),
shuffle_write_records_min bigint,
shuffle_write_records_max bigint,
shuffle_write_records_mean NUMERIC(16,1),
task_duration_total INT,
gc_time_total INT,
shuffle_read_records_total bigint,
shuffle_read_size_total bigint,
shuffle_write_size_total bigint,
shuffle_write_records_total bigint,
failed_reason TEXT,
start_time TIMESTAMP(3),
end_time TIMESTAMP(3),
PRIMARY KEY(parent_job_id, dep_run_id, stage_id, stage_attempt_id),
FOREIGN KEY (dep_run_id) REFERENCES deployment_history(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS events_spark_executors(
dep_run_id BIGINT,
app_attempt_id VARCHAR(150),
executor_id VARCHAR(10),
status VARCHAR(100),
address VARCHAR(100),
rdd_blocks INT,
max_storage_memory INT NOT NULL DEFAULT 0,
used_storage bigint NOT NULL DEFAULT 0,
disk_used bigint NOT NULL DEFAULT 0,
cores INT NOT NULL DEFAULT 0,
active_tasks INT NOT NULL DEFAULT 0,
failed_tasks INT NOT NULL DEFAULT 0,
completed_tasks INT NOT NULL DEFAULT 0,
total_tasks INT NOT NULL DEFAULT 0,
task_time BIGINT NOT NULL DEFAULT 0,
input_bytes BIGINT NOT NULL DEFAULT 0,
shuffle_read BIGINT NOT NULL DEFAULT 0,
shuffle_write BIGINT NOT NULL DEFAULT 0,
dt_added TIMESTAMP(3),
PRIMARY KEY(dep_run_id, executor_id),
FOREIGN KEY(dep_run_id) REFERENCES deployment_history(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS events_spark_task_distribution(
dep_run_id BIGINT,
app_attempt_id VARCHAR(150),
executor_id VARCHAR(10),
status VARCHAR(100),
active_tasks INT NOT NULL DEFAULT 0,
failed_tasks INT NOT NULL DEFAULT 0,
completed_tasks INT NOT NULL DEFAULT 0,
total_tasks INT NOT NULL DEFAULT 0,
ts TIMESTAMP(3),
PRIMARY KEY(dep_run_id, executor_id, ts),
FOREIGN KEY(dep_run_id) REFERENCES deployment_history(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS metrics_spark_runtime(
dep_run_id BIGINT,
app_id VARCHAR(150),
executor_id VARCHAR(10),
jvm_gc_marksweep_count INT,
jvm_gc_marksweep_time INT,
jvm_gc_collection_count INT,
jvm_gc_collection_time INT,
jvm_direct_count INT,
jvm_direct_used INT,
jvm_direct_capacity BIGINT,
jvm_heap_usage NUMERIC(12,2),
jvm_heap_init BIGINT,
jvm_heap_committed BIGINT,
jvm_heap_max BIGINT,
jvm_heap_used BIGINT,
jvm_mapped_count INT,
jvm_mapped_used BIGINT,
jvm_mapped_capacity BIGINT,
jvm_non_heap_max BIGINT,
jvm_non_heap_committed BIGINT,
jvm_non_heap_usage NUMERIC(12,2),
jvm_non_heap_used BIGINT,
jvm_non_heap_init BIGINT,
jvm_pools_code_cache_used BIGINT,
jvm_pools_code_cache_committed BIGINT,
jvm_pools_code_cache_usage NUMERIC(12,2),
jvm_pools_code_cache_init BIGINT,
jvm_pools_code_cache_max BIGINT,
jvm_pools_compressed_class_space_used BIGINT,
jvm_pools_compressed_class_space_init BIGINT,
jvm_pools_compressed_class_space_usage BIGINT,
jvm_pools_compressed_class_space_max BIGINT,
jvm_pools_compressed_class_space_committed BIGINT,
jvm_pools_metaspace_used BIGINT,
jvm_pools_metaspace_committed BIGINT,
jvm_pools_metaspace_usage NUMERIC(12,2),
jvm_pools_metaspace_init BIGINT,
jvm_pools_metaspace_max BIGINT,
jvm_pools_gc_eden_space_init BIGINT,
jvm_pools_gc_eden_space_committed BIGINT,
jvm_pools_gc_eden_space_max BIGINT,
jvm_pools_gc_eden_space_used BIGINT,
jvm_pools_gc_eden_space_usage NUMERIC(12,2),
jvm_pools_gc_old_gen_init BIGINT,
jvm_pools_gc_old_gen_committed BIGINT,
jvm_pools_gc_old_gen_max BIGINT,
jvm_pools_gc_old_gen_used BIGINT,
jvm_pools_gc_old_gen_usage NUMERIC(12,2),
jvm_pools_gc_survivor_space_max BIGINT,
jvm_pools_gc_survivor_space_usage NUMERIC(12,2),
jvm_pools_gc_survivor_space_committed BIGINT,
jvm_pools_gc_survivor_space_used BIGINT,
jvm_pools_gc_survivor_space_init BIGINT,
jvm_total_init BIGINT,
jvm_total_committed BIGINT,
jvm_total_max BIGINT,
jvm_total_used BIGINT,
jvm_load_average BIGINT,
jvm_file_descriptors_open INT,
jvm_file_descriptors_max INT,
jvm_mem_committed BIGINT,
jvm_mem_size BIGINT,
jvm_mem_free BIGINT,
jvm_cpu_num_available INT,
jvm_cpu_process_usage NUMERIC(12,2),
jvm_cpu_usage NUMERIC(12,2),
jvm_swap_size BIGINT,
jvm_swap_free BIGINT,
ts TIMESTAMP(3),
PRIMARY KEY(dep_run_id, executor_id, ts),
FOREIGN KEY (dep_run_id) REFERENCES deployment_history(id) ON DELETE CASCADE
);

CREATE EXTENSION IF NOT EXISTS timescaledb;
SELECT create_hypertable('metrics_spark_runtime', 'ts');
SELECT add_dimension('metrics_spark_runtime', 'dep_run_id', number_partitions => 4);
SELECT add_dimension('metrics_spark_runtime', 'executor_id', number_partitions => 4);


SELECT create_hypertable('events_spark_task_distribution', 'ts');
SELECT add_dimension('events_spark_task_distribution', 'dep_run_id', number_partitions => 4);
SELECT add_dimension('events_spark_task_distribution', 'executor_id', number_partitions => 4);

CREATE TABLE IF NOT EXISTS alert_spark_metrics_rules(
alert_id BIGINT NOT NULL,
job_id INT,
name VARCHAR(500),
alert_rule JSON,
create_ts TIMESTAMP(3),
PRIMARY KEY(job_id, name),
FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS notification_channels(
channel_id SERIAL PRIMARY KEY,
org_id INT,
channel_type VARCHAR(20),
create_ts TIMESTAMP(3),
subscribers JSON,
FOREIGN KEY (org_id) REFERENCES orgs(id) ON DELETE CASCADE
);

--Check for deletion of this table
CREATE TABLE IF NOT EXISTS agents(
id VARCHAR(200),
org_id INT,
name VARCHAR(250),
status VARCHAR(20),
dt_registered TIMESTAMP(3),
dt_ping TIMESTAMP(3),
FOREIGN KEY (org_id) REFERENCES orgs(id) ON DELETE CASCADE
);

-- Marked for deletion
CREATE TABLE IF NOT EXISTS deployment_action_log(
deployment_run_id INT,
action_name VARCHAR(250),
seq INT,
action_id VARCHAR(250),
status VARCHAR(20),
dt_last_updated TIMESTAMP(3),
dt_started TIMESTAMP(3),
PRIMARY KEY(deployment_run_id, action_id),
FOREIGN KEY (deployment_run_id) REFERENCES deployment_history(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS errors(
job_run_id INT,
attempt_id VARCHAR(100),
cause TEXT,
severity_level VARCHAR(20),
error_source VARCHAR(50),
error_object JSON,
dt_created TIMESTAMP(3),
FOREIGN KEY (job_run_id) REFERENCES job_history(id) ON DELETE CASCADE
);

--TODO: To be removed from the code
CREATE TABLE IF NOT EXISTS user_keys(
id BIGSERIAL PRIMARY KEY,
member_id BIGINT,
hex_pub_key VARCHAR(500),
created TIMESTAMP(3),
FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS secrets_pool(
id BIGSERIAL PRIMARY KEY,
name VARCHAR(500),
category VARCHAR(50),
workspace_id BIGINT,
FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS workspace_secrets(
key varchar(200),
secret_pool_id BIGINT,
key_version varchar(200),
PRIMARY KEY(key, secret_pool_id),
FOREIGN KEY (secret_pool_id) REFERENCES secrets_pool(id) ON DELETE CASCADE
);

CREATE TABLE workspace_crypto_keypairs(
hex_pub_key VARCHAR(1000),
encrypted_pkey VARCHAR(2000),
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
encrypted_pkey TEXT,
integration_type VARCHAR(100),
org_id BIGINT,
FOREIGN KEY (org_id) REFERENCES orgs(id) ON DELETE cascade
);

CREATE TABLE IF NOT EXISTS cluster_metric(
id BIGINT PRIMARY KEY,
active_apps INT,
completed_apps INT,
failed_apps INT,
last_updated TIMESTAMP(3),
FOREIGN KEY (id) REFERENCES clusters(id) ON DELETE cascade
);


CREATE TABLE IF NOT EXISTS cluster_node_metric(
id varchar(100),
cluster_id BIGINT,
host VARCHAR(100),
port VARCHAR(100),
total_cpu INT,
total_memory BIGINT,
last_updated TIMESTAMP(3),
PRIMARY KEY(id, cluster_id),
FOREIGN KEY (cluster_id) REFERENCES clusters(id) ON DELETE cascade
);
CREATE INDEX cluster_node_metric_cluster_id_idx ON cluster_node_metric (cluster_id);

CREATE TABLE IF NOT EXISTS usage_plans(
id BIGSERIAL,
name VARCHAR(10),
max_local_clusters_count INT,
max_remote_cluster_count INT,
max_remote_cluster_size INT,
max_jobs_count INT,
max_workspace_count INT,
max_job_duration INT DEFAULT 600,
org_id BIGINT,
FOREIGN KEY (org_id) REFERENCES orgs(id) ON DELETE cascade
);

CREATE TABLE IF NOT EXISTS sandbox_cluster(
id BIGSERIAL,
version VARCHAR(10),
service_options JSON
);

CREATE TABLE IF NOT EXISTS feature_notification_requests(
    id BIGSERIAL,
    member_id BIGINT,
    dt_requested TIMESTAMP(3),
    FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
);
