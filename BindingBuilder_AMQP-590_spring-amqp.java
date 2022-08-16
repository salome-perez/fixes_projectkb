public class BindingBuilder {
			public Binding exists() {
				return new Binding(HeadersExchangeMapConfigurer.this.destination.name,
						HeadersExchangeMapConfigurer.this.destination.type,
						HeadersExchangeMapConfigurer.this.exchange.getName(), "", createMapForKeys(this.key));
			}

			public Binding exist() {
				return new Binding(HeadersExchangeMapConfigurer.this.destination.name,
						HeadersExchangeMapConfigurer.this.destination.type,
						HeadersExchangeMapConfigurer.this.exchange.getName(), "", this.headerMap);
			}

		public Binding noargs() {
			return new Binding(this.configurer.destination.name, this.configurer.destination.type, this.configurer.exchange,
					this.routingKey, Collections.<String, Object> emptyMap());
		}

		public Binding and(Map<String, Object> map) {
			return new Binding(this.configurer.destination.name, this.configurer.destination.type, this.configurer.exchange,
					this.routingKey, map);
		}

			public Binding match() {
				return new Binding(HeadersExchangeMapConfigurer.this.destination.name,
						HeadersExchangeMapConfigurer.this.destination.type,
						HeadersExchangeMapConfigurer.this.exchange.getName(), "", this.headerMap);
			}

			public Binding matches(Object value) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put(this.key, value);
				return new Binding(HeadersExchangeMapConfigurer.this.destination.name,
						HeadersExchangeMapConfigurer.this.destination.type,
						HeadersExchangeMapConfigurer.this.exchange.getName(), "", map);
			}

}