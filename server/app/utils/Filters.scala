package utils

import javax.inject.Inject
import play.api.http.HttpFilters
import play.api.mvc.EssentialFilter
import play.filters.cors.CORSFilter
import play.filters.csrf.CSRFFilter
import play.filters.headers.SecurityHeadersFilter

/**
 * Provides filters.
 */
class Filters @Inject() (csrfFilter: CSRFFilter, securityHeadersFilter: SecurityHeadersFilter, corsFilter: CORSFilter) extends HttpFilters {
  override def filters: Seq[EssentialFilter] = Seq(corsFilter, csrfFilter, securityHeadersFilter)
}
