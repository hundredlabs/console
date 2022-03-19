import React, { useEffect, useState } from "react";
export abstract class ConnectionConfig {
  name: string;
}

export interface AWSS3ConnectionConfig extends ConnectionConfig {
  name: string;
  awsKey: string;
  awsSecretKey: string;
  region: string;
  buckets: string[];
}

export interface MySQLConnectionConfig extends ConnectionConfig {
  name: string;
  host: string;
  port: string;
  database: string;
  password: string;
  useSSL: boolean;
}

export {};
