public class HtmlSessionInformationsReport {
	private void writeSession(SessionInformations session, boolean displayUser) throws IOException {
		final String nextColumnAlignRight = "</td><td align='right'>";
		final String nextColumnAlignCenter = "</td><td align='center'>";
		write("<td><a href='?part=sessions&amp;sessionId=");
		write(htmlEncode(session.getId()));
		write("'>");
		write(htmlEncode(session.getId()));
		write("</a>");
		write(nextColumnAlignRight);
		write(durationFormat.format(session.getLastAccess()));
		write(nextColumnAlignRight);
		write(durationFormat.format(session.getAge()));
		write(nextColumnAlignRight);
		write(expiryFormat.format(session.getExpirationDate()));

		write(nextColumnAlignRight);
		write(integerFormat.format(session.getAttributeCount()));
		write(nextColumnAlignCenter);
		if (session.isSerializable()) {
			write("#oui#");
		} else {
			write("<span class='severe'>#non#</span>");
		}
		write(nextColumnAlignRight);
		write(integerFormat.format(session.getSerializedSize()));
		final String nextColumn = "</td><td>";
		write(nextColumn);
		final String remoteAddr = session.getRemoteAddr();
		if (remoteAddr == null) {
			write("&nbsp;");
		} else {
			write(remoteAddr);
		}
		write(nextColumnAlignCenter);
		writeCountry(session);
		if (displayUser) {
			write(nextColumn);
			final String remoteUser = session.getRemoteUser();
			if (remoteUser == null) {
				write("&nbsp;");
			} else {
				writer.write(htmlEncode(remoteUser));
			}
		}
		write("</td><td align='center' class='noPrint'>");
		write(A_HREF_PART_SESSIONS);
		write("&amp;action=invalidate_session&amp;sessionId=");
		write(I18N.urlEncode(session.getId()));
		write("' onclick=\"javascript:return confirm('"
				+ I18N.getStringForJavascript("confirm_invalidate_session") + "');\">");
		write("<img width='16' height='16' src='?resource=user-trash.png' alt='#invalidate_session#' title='#invalidate_session#' />");
		write("</a>");
		write("</td>");
	}

	private void writeBackAndRefreshLinksForSession(String sessionId) throws IOException {
		writeln("<div class='noPrint'>");
		writeln("<a href='javascript:history.back()'><img src='?resource=action_back.png' alt='#Retour#'/> #Retour#</a>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
		writeln(A_HREF_PART_SESSIONS + "&amp;sessionId=" + I18N.urlEncode(sessionId) + "'>");
		writeln("<img src='?resource=action_refresh.png' alt='#Actualiser#'/> #Actualiser#</a>");
		writeln("</div>");
	}

	void writeSessionDetails(String sessionId, SessionInformations sessionInformations)
			throws IOException {
		writeBackAndRefreshLinksForSession(sessionId);
		writeln("<br/>");

		if (sessionInformations == null) {
			writeln(I18N.getFormattedString("session_invalidee", htmlEncode(sessionId)));
			return;
		}
		writeln("<img width='24' height='24' src='?resource=system-users.png' alt='#Sessions#' />&nbsp;");
		writeln("<b>" + I18N.getFormattedString("Details_session", htmlEncode(sessionId)) + "</b>");
		writeSessions(Collections.singletonList(sessionInformations));

		writeln("<br/><b>#Attributs#</b>");
		writeSessionAttributes(sessionInformations);
	}

}