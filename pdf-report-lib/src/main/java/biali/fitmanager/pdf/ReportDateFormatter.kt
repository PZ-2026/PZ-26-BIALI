package biali.fitmanager.pdf

internal object ReportDateFormatter {

  fun format(dateString: String?): String {
    if (dateString.isNullOrBlank() || dateString.equals("null", ignoreCase = true)) return ""
    var s = dateString.trim()

    if (Regex("^\\d{2}\\.\\d{2}\\.\\d{4}$").matches(s)) return s
    if (s.contains(" ")) s = s.substringBefore(" ")

    Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(s)?.let {
      val (year, month, day) = it.destructured
      return "$day.$month.$year"
    }

    Regex("^(\\d{4})/(\\d{2})/(\\d{2})$").find(s)?.let {
      val (year, month, day) = it.destructured
      return "$day.$month.$year"
    }

    Regex("^(\\d{2})/(\\d{2})/(\\d{4})$").find(s)?.let {
      val (day, month, year) = it.destructured
      return "$day.$month.$year"
    }

    return s
  }
}
