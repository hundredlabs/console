package web.providers

import javax.inject.Inject
import web.models.Member
import web.repo.MemberRepository

import scala.concurrent.Future

class DesktopTokenCredProvider @Inject()(memberRepository: MemberRepository) {

  def authenticateWith(token: String): Future[Option[Member]] = memberRepository.findByToken(token)

}
