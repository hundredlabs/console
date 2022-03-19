package web.services

import com.gigahex.services.ServiceConnection

import scala.concurrent.Future

trait DataService {

  def testConnection(connection: ServiceConnection): Future[Either[Throwable, Boolean]]

}
