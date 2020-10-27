/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.microsoft.sqlserver.jdbc.spark

import com.microsoft.sqlserver.jdbc.spark.utils.BulkCopyUtils._

import org.apache.spark.internal.Logging
import org.apache.spark.sql.{DataFrame, SaveMode, SQLContext}
import org.apache.spark.sql.execution.datasources.jdbc.JdbcRelationProvider
import org.apache.spark.sql.execution.datasources.jdbc.JdbcUtils.createConnectionFactory
import org.apache.spark.sql.sources.BaseRelation

/**
 * DefaultSource extends JDBCRelationProvider to provide a implementation for
 * MSSQLSpark connector.  Only write function is overridden.
 * Read functionality not overridden and is re-used from default JDBC connector.
 * Read for datapool external tables is supported from Master instance that's
 * handled via JDBC logic.
 */
class DefaultSource extends JdbcRelationProvider with Logging {

  /**
   * shortName overrides datasource interface to provide an alias to access mssql spark connector.
   */
  override def shortName(): String = "mssql"

  /**
   * createRelation overrides createRelations from JdbcRelationProvider to implement custom write
   * for both SQLServer Master instance and Data pools. The choice is made at run time based on
   * based on the passed parameter map.
   * @param sqlContext SQLContext passed from spark jdbc datasource framework.
   * @param mode as passed from spark jdbc datasource framework
   * @param parameters User options passed as a parameter map
   * @param rawDf raw dataframe passed from spark data source framework
   *
   */
  override def createRelation(
      sqlContext: SQLContext,
      mode: SaveMode,
      parameters: Map[String, String],
      rawDf: DataFrame): BaseRelation = {
    val options = new SQLServerBulkJdbcOptions(parameters)
    val conn = createConnectionFactory(options)()
    val df = repartitionDataFrame(rawDf, options)

    logDebug("createRelations: Write request. Connection catalogue is" + s"${conn.getCatalog}")
    logDebug(
      s"createRelations: Write request. ApplicationId is ${sqlContext.sparkContext.applicationId}")
    try {
      checkIsolationLevel(conn, options)
      val connector = ConnectorFactory.get(options)
      connector.write(sqlContext, mode, df, conn, options)
    } finally {
      logDebug("createRelations: Closing connection")
      conn.close()
    }
    logDebug("createRelations: Exiting")
    super.createRelation(sqlContext, parameters)
  }
}
