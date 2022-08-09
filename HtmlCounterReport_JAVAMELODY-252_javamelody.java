public class HtmlCounterReport {
		void writeRequestAndGraphDetail(Collector collector, CollectorServer collectorServer,
				String graphName) throws IOException {
			counters = collector.getRangeCountersToBeDisplayed(range);
			requestsById = mapAllRequestsById();
			final CounterRequest request = requestsById.get(graphName);
			if (request != null) {
				writeRequest(request);

				if (JdbcWrapper.SINGLETON.getSqlCounter().isRequestIdFromThisCounter(graphName)
						&& !request.getName().toLowerCase().startsWith("alter ")) {
					// inutile d'essayer d'avoir le plan d'exécution des requêtes sql
					// telles que "alter session set ..." (cf issue 152)
					writeSqlRequestExplainPlan(collector, collectorServer, request);
				}
			}
			if (isGraphDisplayed(collector, request)) {
				writeln("<div id='track' class='noPrint'>");
				writeln("<div class='selected' id='handle'>");
				writeln("<img src='?resource=scaler_slider.gif' alt=''/>");
				writeln("</div></div>");

				writeln("<div align='center'><img class='synthèse' id='img' src='"
						+ "?width=960&amp;height=400&amp;graph=" + I18N.urlEncode(graphName)
						+ "' alt='zoom'/></div>");
				writeln("<div align='right'><a href='?part=lastValue&amp;graph="
						+ I18N.urlEncode(graphName)
						+ "' title=\"#Lien_derniere_valeur#\">_</a></div>");

				writeGraphDetailScript(graphName);
			}
			if (request != null && request.getStackTrace() != null) {
				writeln("<blockquote><blockquote><b>Stack-trace</b><br/><font size='-1'>");
				// writer.write pour ne pas gérer de traductions si la stack-trace contient '#'
				writer.write(htmlEncode(request.getStackTrace()).replaceAll("\t",
						"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"));
				writeln("</font></blockquote></blockquote>");
			}
		}

		private void writeGraphDetailScript(String graphName) throws IOException {
			writeln(SCRIPT_BEGIN);
			writeln("function scaleImage(v, min, max) {");
			writeln("    var images = document.getElementsByClassName('synthèse');");
			writeln("    w = (max - min) * v + min;");
			writeln("    for (i = 0; i < images.length; i++) {");
			writeln("        images[i].style.width = w + 'px';");
			writeln("    }");
			writeln("}");

			// 'animate' our slider
			writeln("var slider = new Control.Slider('handle', 'track', {axis:'horizontal', alignX: 0, increment: 2});");

			// resize the image as the slider moves. The image quality would deteriorate, but it
			// would not be final anyway. Once slider is released the image is re-requested from the server, where
			// it is rebuilt from vector format
			writeln("slider.options.onSlide = function(value) {");
			writeln("  scaleImage(value, initialWidth, initialWidth / 2 * 3);");
			writeln("}");

			// this is where the slider is released and the image is reloaded
			// we use current style settings to work the required image dimensions
			writeln("slider.options.onChange = function(value) {");
			// chop off "px" and round up float values
			writeln("  width = Math.round(Element.getStyle('img','width').replace('px','')) - 80;");
			writeln("  height = Math.round(width * initialHeight / initialWidth) - 48;");
			// reload the images
			// rq : on utilise des caractères unicode pour éviter des warnings
			writeln("  document.getElementById('img').src = '?graph=" + I18N.urlEncode(graphName)
					+ "\\u0026width=' + width + '\\u0026height=' + height;");
			writeln("  document.getElementById('img').style.width = '';");
			writeln("}");
			writeln("window.onload = function() {");
			writeln("  if (navigator.appName == 'Microsoft Internet Explorer') {");
			writeln("    initialWidth = document.getElementById('img').width;");
			writeln("    initialHeight = document.getElementById('img').height;");
			writeln("  } else {");
			writeln("    initialWidth = Math.round(Element.getStyle('img','width').replace('px',''));");
			writeln("    initialHeight = Math.round(Element.getStyle('img','height').replace('px',''));");
			writeln("  }");
			writeln("}");
			writeln(SCRIPT_END);
		}

}