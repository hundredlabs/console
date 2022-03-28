package com.gigahex.services

case class DatabaseServer(majorVersion: Int, minorVersion: Int, productName: String, patchVersion: Int = 0)
case class RequestQueryExecution(q: String)