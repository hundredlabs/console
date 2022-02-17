package utils

case class ResourceNotFound(message: String) extends RuntimeException {
  override def getMessage: String = message
}

