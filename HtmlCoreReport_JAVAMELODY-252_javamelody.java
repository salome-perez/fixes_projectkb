public class HtmlCoreReport {
	void writeRefreshAndPeriodLinks(String graphName, String part) throws IOException {
		writeln("<div class='noPrint'>");
		final String separator = "&nbsp;&nbsp;&nbsp;&nbsp;";
		final String graphParameter = "&amp;graph=";
		if (graphName == null) {
			write("<a href='?' title='#Rafraichir#'>");
		} else {
			write("<a href='javascript:history.back()'><img src='?resource=action_back.png' alt='#Retour#'/> #Retour#</a>");
			writeln(separator);
			writeln("<a href='?'><img src='?resource=action_home.png' alt='#Page_principale#'/> #Page_principale#</a>");
			writeln(separator);
			write("<a href='?part=" + part + graphParameter + I18N.urlEncode(graphName)
					+ "' title='#Rafraichir#'>");
		}
		write("<img src='?resource=action_refresh.png' alt='#Actualiser#'/> #Actualiser#</a>");
		if (graphName == null && PDF_ENABLED) {
			writeln(separator);
			write("<a href='?format=pdf' title='#afficher_PDF#'>");
			write("<img src='?resource=pdf.png' alt='#PDF#'/> #PDF#</a>");
		}
		writeln(separator);
		write("<a href='?resource=#help_url#' target='_blank'");
		write(" title=\"#Afficher_aide_en_ligne#\"><img src='?resource=action_help.png' alt='#Aide_en_ligne#'/> #Aide_en_ligne#</a>");
		writeln(separator);
		writeln("#Choix_periode# :&nbsp;");
		// On affiche des liens vers les périodes.
		// Rq : il n'y a pas de période ni de graph sur la dernière heure puisque
		// si la résolution des données est de 5 min, on ne verra alors presque rien
		for (final Period myPeriod : Period.values()) {
			if (graphName == null) {
				write("<a href='?period=" + myPeriod.getCode() + "' ");
			} else {
				write("<a href='?part=" + part + graphParameter + I18N.urlEncode(graphName)
						+ "&amp;period=" + myPeriod.getCode() + "' ");
			}
			write("title='" + I18N.getFormattedString("Choisir_periode", myPeriod.getLinkLabel())
					+ "'>");
			write("<img src='?resource=" + myPeriod.getIconName() + "' alt='"
					+ myPeriod.getLinkLabel() + "' /> ");
			writeln(myPeriod.getLinkLabel() + "</a>&nbsp;");
		}
		new HtmlForms(writer).writeCustomPeriodLink(range, graphName, part);

		writeln(END_DIV);
	}

		void writeCustomPeriodLink(Range range, String graphName, String part) throws IOException {
			writeln("<a href=\"javascript:showHide('customPeriod');document.customPeriodForm.startDate.focus();\" ");
			final String linkLabel = I18N.getString("personnalisee");
			writeln("title='" + I18N.getFormattedString("Choisir_periode", linkLabel) + "'>");
			writeln("<img src='?resource=calendar.png' alt='#personnalisee#' /> #personnalisee#</a>");
			writeln("<div id='customPeriod' style='display: none;'>");
			writeln(SCRIPT_BEGIN);
			writeln("function validateCustomPeriodForm() {");
			writeln("   periodForm = document.customPeriodForm;");
			writelnCheckMandatory("periodForm.startDate", "dates_mandatory");
			writelnCheckMandatory("periodForm.endDate", "dates_mandatory");
			writeln("   periodForm.period.value=periodForm.startDate.value + '-' + periodForm.endDate.value;");
			writeln("   return true;");
			writeln("}");
			writeln(SCRIPT_END);
			writeln("<br/><br/>");
			final DateFormat dateFormat = I18N.createDateFormat();
			final String dateFormatPattern;
			if (I18N.getString("dateFormatPattern").length() == 0) {
				final String pattern = ((SimpleDateFormat) dateFormat).toPattern();
				dateFormatPattern = pattern.toLowerCase(I18N.getCurrentLocale());
			} else {
				dateFormatPattern = I18N.getString("dateFormatPattern");
			}
			writeln("<form name='customPeriodForm' method='get' action='' onsubmit='return validateCustomPeriodForm();'>");
			writeln("<br/><b>#startDate#</b>&nbsp;&nbsp;<input type='text' size='10' name='startDate' ");
			if (range.getStartDate() != null) {
				writeln("value='" + dateFormat.format(range.getStartDate()) + '\'');
			}
			writeln("/>&nbsp;&nbsp;<b>#endDate#</b>&nbsp;&nbsp;<input type='text' size='10' name='endDate' ");
			if (range.getEndDate() != null) {
				writeln("value='" + dateFormat.format(range.getEndDate()) + '\'');
			}
			writeln("/>&nbsp;&nbsp;");
			writer.write('(' + dateFormatPattern + ')');
			writeln("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<input type='submit' value='#ok#'/><br/><br/>");
			writeln("<input type='hidden' name='period' value=''/>");
			if (graphName != null) {
				writeln("<input type='hidden' name='part' value='" + part + "'/>");
				writeln("<input type='hidden' name='graph' value='" + I18N.urlEncode(graphName)
						+ "'/>");
			}
			writeln("</form><br/>");
			writeln(END_DIV);
		}

	void writeCounterSummaryPerClass(String counterName, String requestId) throws IOException {
		final Counter counter = collector.getRangeCounter(range, counterName);
		writeln("<div class='noPrint'>");
		writeln("<a href='javascript:history.back()'><img src='?resource=action_back.png' alt='#Retour#'/> #Retour#</a>");
		final String separator = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ";
		writeln(separator);
		final String hrefStart = "<a href='?part=counterSummaryPerClass&amp;counter="
				+ counter.getName()
				+ (requestId == null ? "" : "&amp;graph=" + I18N.urlEncode(requestId));
		writeln(hrefStart + "'>");
		writeln("<img src='?resource=action_refresh.png' alt='#Actualiser#'/> #Actualiser#</a>");

		if (PDF_ENABLED) {
			writeln(separator);
			write(hrefStart);
			writeln("&amp;format=pdf' title='#afficher_PDF#'>");
			write("<img src='?resource=pdf.png' alt='#PDF#'/> #PDF#</a>");
		}
		writeln("</div>");

		writeCounterTitle(counter);
		final HtmlCounterReport htmlCounterReport = new HtmlCounterReport(counter, range, writer);
		htmlCounterReport.writeRequestsAggregatedOrFilteredByClassName(requestId);
	}

}