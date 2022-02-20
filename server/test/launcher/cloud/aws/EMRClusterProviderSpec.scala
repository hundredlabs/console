package web.cloud.aws


import org.scalatest.FutureOutcome
import org.scalatest.flatspec.FixtureAsyncFlatSpec
import org.scalatest.matchers.must.Matchers

class EMRClusterProviderSpec extends FixtureAsyncFlatSpec with Matchers {

  val apiKey = sys.env("AWS_API_KEY")
  val secret = sys.env("AWS_API_SECRET_KEY")
  behavior of "EMR Cluster"

  override def withFixture(test: OneArgAsyncTest): FutureOutcome = ???

  override type FixtureParam = this.type
}