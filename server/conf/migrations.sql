COPY alpha_requests(req_id ,
                    email ,
                    dt_requested,
                    code_sent
                    )
FROM '/home/admin/alpha_requests.csv'
DELIMITER ';'
CSV HEADER;

COPY approved_requests (
req_id,
email ,
activation_code,
activation_status,
dt_approved
)
FROM '/home/admin/approved_requests.csv'
 DELIMITER ';'
 CSV HEADER;


 COPY members (
 id,
 name,
 email,
 member_type,
 dt_joined,
 receive_updates
 )
 FROM '/home/admin/members.csv'
 DELIMITER ';'
 CSV HEADER;
 ;

 COPY orgs (
 id ,
 name,
 owner,
 api_key,
 secret_key,
 api_key_validity_days,
 dt_created
 )
 FROM '/home/admin/orgs.csv'
 DELIMITER ';'
 CSV HEADER;


 COPY credentials (
  id ,
  email,
  hasher,
  password,
  salt
  )
  FROM '/home/admin/credentials.csv'
  DELIMITER ';'
  CSV HEADER;


 COPY jobs (
  id,
  name,
  description,
  org_id,
  dt_created ,
  last_updated,
  job_type
 )
  FROM '/home/admin/jobs.csv'
  DELIMITER ';'
  CSV HEADER;


 COPY job_history (
  id ,
  job_id ,
  status ,
  dt_started ,
  last_updated,
  run_index ,
  soft_deleted ,
  deployment_run_id ,
  action_id
  )
  FROM '/home/admin/job_history.csv'
  DELIMITER ';'
  CSV HEADER;

  
 COPY spark_project_config (
    project_id,
    interval_progress_events,
    interval_metrics,
    interval_log_events
 )
  FROM '/home/admin/spark_project_config.csv'
  DELIMITER ';'
  CSV HEADER;

 
 
  COPY project_stats (
    project_id ,
     avg_runtime,
     avg_memory_usage ,
     avg_cpu_usage 
  )
   FROM '/home/admin/project_stats.csv'
   DELIMITER ';'
   CSV HEADER;

  
 COPY events_spark_app (
 job_run_id ,
 app_id ,
 spark_version ,
 master ,
 executor_mem ,
 driver_mem ,
 executor_cores,
 memory_overhead ,
 app_name,
 spark_user ,
 user_timezone,
 app_attempt_id,
 app_status ,
 total_executor_runtime,
 total_gc_time ,
 avg_task_runtime ,
 max_task_runtime ,
 last_updated ,
 start_time ,
 end_time
   )
    FROM '/home/admin/events_spark_app.csv'
    DELIMITER ';'
    CSV HEADER;
   ;
 
  COPY events_spark_jobs (
  name ,
  job_run_id ,
  job_id ,
  app_id ,
  app_attempt_id ,
  job_status ,
  num_stages,
  num_stages_completed ,
  failed_reason ,
  start_time ,
  end_time
  )
   FROM '/home/admin/events_spark_jobs.csv'
   DELIMITER ';'
   CSV HEADER;
    ;

 
    COPY events_spark_stage (
   job_run_id ,
   parent_job_id ,
   stage_id ,
   name ,
   app_attempt_id ,
   bytes_read big ,
   records_read big ,
   bytes_written big ,
   records_written big ,
   stage_attempt_id ,
   stage_status ,
   num_tasks ,
   num_tasks_completed ,
   scheduler_delay ,
   executor_compute_time ,
   shuffle_read_time ,
   shuffle_write_time ,
   getting_result_time ,
   task_deserialization_time ,
   result_deserialization_time ,
   task_duration_min ,
   task_duration_max ,
   task_duration_mean ,
   gc_time_min ,
   gc_time_max ,
   gc_time_mean ,
   shuffle_read_records_min ,
   shuffle_read_records_max ,
   shuffle_read_records_mean ,
   shuffle_read_size_min ,
   shuffle_read_size_max ,
   shuffle_read_size_mean ,
   shuffle_write_size_min ,
   shuffle_write_size_max ,
   shuffle_write_size_mean ,
   shuffle_write_records_min ,
   shuffle_write_records_max ,
   shuffle_write_records_mean ,
   task_duration_total ,
   gc_time_total ,
   shuffle_read_records_total ,
   shuffle_read_size_total ,
   shuffle_write_size_total ,
   shuffle_write_records_total ,
   failed_reason TEXT,
   start_time ,
   end_time 
     )
      FROM '/home/admin/events_spark_stage.csv'
      DELIMITER ';'
      CSV HEADER;
       
   
  COPY events_spark_executors (
  job_run_id ,
  app_attempt_id ,
  executor_id ,
  status ,
  address ,
  rdd_blocks 
  max_storage_memory ,
  used_storage big,
  disk_used big,
  cores ,
  active_tasks ,
  failed_tasks ,
  completed_tasks ,
  total_tasks ,
  task_time ,
  input_bytes ,
  shuffle_read,
  shuffle_write,
  dt_added 
  )
  FROM '/home/admin/events_spark_executors.csv'
  DELIMITER ';'
  CSV HEADER;
  
  COPY events_spark_task_distribution (
    job_run_id ,
    app_attempt_id ,
    executor_id ,
    status ,
    active_tasks ,
    failed_tasks ,
    completed_tasks ,
    total_tasks ,
    ts 
    )
  FROM '/home/admin/events_spark_task_distribution.csv'
  DELIMITER ';'
  CSV HEADER;

  COPY metrics_spark_runtime (
    job_run_id ,
    app_id ,
    executor_id ,
    jvm_gc_marksweep_count ,
    jvm_gc_marksweep_time ,
    jvm_gc_collection_count ,
    jvm_gc_collection_time ,
    jvm_direct_count ,
    jvm_direct_used ,
    jvm_direct_capacity ,
    jvm_heap_usage ,
    jvm_heap_init ,
    jvm_heap_committed ,
    jvm_heap_max ,
    jvm_heap_used ,
    jvm_mapped_count ,
    jvm_mapped_used ,
    jvm_mapped_capacity ,
    jvm_non_heap_max ,
    jvm_non_heap_committed ,
    jvm_non_heap_usage ,
    jvm_non_heap_used ,
    jvm_non_heap_init ,
    jvm_pools_code_cache_used ,
    jvm_pools_code_cache_committed ,
    jvm_pools_code_cache_usage ,
    jvm_pools_code_cache_init ,
    jvm_pools_code_cache_max ,
    jvm_pools_compressed_class_space_used ,
    jvm_pools_compressed_class_space_init ,
    jvm_pools_compressed_class_space_usage ,
    jvm_pools_compressed_class_space_max ,
    jvm_pools_compressed_class_space_committed ,
    jvm_pools_metaspace_used ,
    jvm_pools_metaspace_committed ,
    jvm_pools_metaspace_usage ,
    jvm_pools_metaspace_init ,
    jvm_pools_metaspace_max ,
    jvm_pools_gc_eden_space_init ,
    jvm_pools_gc_eden_space_committed ,
    jvm_pools_gc_eden_space_max ,
    jvm_pools_gc_eden_space_used ,
    jvm_pools_gc_eden_space_usage ,
    jvm_pools_gc_old_gen_init ,
    jvm_pools_gc_old_gen_committed ,
    jvm_pools_gc_old_gen_max ,
    jvm_pools_gc_old_gen_used ,
    jvm_pools_gc_old_gen_usage ,
    jvm_pools_gc_survivor_space_max ,
    jvm_pools_gc_survivor_space_usage ,
    jvm_pools_gc_survivor_space_committed ,
    jvm_pools_gc_survivor_space_used ,
    jvm_pools_gc_survivor_space_init ,
    jvm_total_init ,
    jvm_total_committed ,
    jvm_total_max ,
    jvm_total_used ,
    jvm_load_average ,
    jvm_file_descriptors_open ,
    jvm_file_descriptors_max ,
    jvm_mem_committed ,
    jvm_mem_size ,
    jvm_mem_free ,
    jvm_cpu_num_available ,
    jvm_cpu_process_usage ,
    jvm_cpu_usage ,
    jvm_swap_size ,
    jvm_swap_free ,
    ts
    )
  FROM '/home/admin/metrics_spark_runtime.csv'
  DELIMITER ';'
  CSV HEADER;


 COPY alert_spark_metrics_rules (
 alert_id ,
 job_id,
 name ,
 alert_rule,
 create_ts
 )
 FROM '/home/admin/alert_spark_metrics_rules.csv'
 DELIMITER ';'
 CSV HEADER;

  COPY notification_channels (
  channel_id,
  org_id,
  channel_type,
  create_ts,
  subscribers
  )
  FROM '/home/admin/notification_channels.csv'
  DELIMITER ';'
  CSV HEADER;

COPY agents (
    id ,
    org_id ,
    name ,
    status ,
    dt_registered ,
    dt_ping
    )
    FROM '/home/admin/agents.csv'
    DELIMITER ';'
    CSV HEADER;


    COPY deployment_config (
      id  ,
      project_id ,
      org_id ,
      name ,
      agent_id ,
      actions ,
      dt_created
      )
      FROM '/home/admin/deployment_config.csv'
      DELIMITER ';'
      CSV HEADER;

 COPY deployment_history (
  id ,
  deployment_id ,
  run_index ,
  trigger_method ,
  status ,
  dt_last_updated ,
  dt_started
  )
  FROM '/home/admin/deployment_history.csv'
  DELIMITER ';'
  CSV HEADER;

 COPY deployment_action_log (
   deployment_run_id ,
   action_name ,
   seq,
   action_id ,
   status ,
   dt_last_updated ,
   dt_started
   )
   FROM '/home/admin/deployment_action_log.csv'
   DELIMITER ';'
   CSV HEADER;

  COPY errors (
     job_run_id ,
     attempt_id ,
     cause ,
     severity_level ,
     error_source ,
     error_object ,
     dt_created
     )
  FROM '/home/admin/errors.csv'
  DELIMITER ';'
  CSV HEADER;

