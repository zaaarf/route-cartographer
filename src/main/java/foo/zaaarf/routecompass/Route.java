package foo.zaaarf.routecompass;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Representation of a REST route.
 */
public class Route {
	/**
	 * The path of the endpoint.
	 */
	public final String path;

	/**
	 * The supported {@link RequestMethod}s, flattened to a string.
	 */
	public final String method;

	/**
	 * The {@link MediaType} produced by the endpoint.
	 * May be null if not specified.
	 */
	public final String produces;

	/**
	 * The {@link MediaType} consumed by the endpoint.
	 * May be null if not specified.
	 */
	public final String consumes;

	/**
	 * Whether the endpoint is deprecated.
	 */
	public final boolean deprecated;

	/**
	 * An array of {@link Param}s, representing parameters accepted by the endpoint.
	 */
	public final Param[] params;

	/**
	 * The one and only constructor.
	 * @param path the path of the endpoint
	 * @param methods the {@link RequestMethod}s accepted by the endpoint
	 * @param consumes the {@link MediaType} consumed by the endpoint, may be null
	 * @param produces the {@link MediaType} produced by the endpoint, may be null
	 * @param deprecated whether the endpoint is deprecated
	 * @param params {@link Param}s of the endpoint, may be null
	 */
	public Route(String path, RequestMethod[] methods, MediaType consumes, MediaType produces, boolean deprecated,
	             Param... params) {
		this.path = path;

		StringBuilder methodStringBuilder = new StringBuilder("[");
		for(RequestMethod m : methods)
			methodStringBuilder
				.append(m.name())
				.append("|");
		methodStringBuilder
			.deleteCharAt(methodStringBuilder.length() - 1)
			.append("]");
		this.method = methodStringBuilder.toString();

		if(produces != null) this.produces = produces.toString();
		else this.produces = null;

		if(consumes != null) this.consumes = consumes.toString();
		else this.consumes = null;

		this.deprecated = deprecated;

		if(params != null) this.params = params;
		else this.params = new Param[0]; //just in case
	}

	/**
	 * Representation of a parameter of a REST route.
	 */
	public static class Param {
		/**
		 * The fully-qualified name of the expected type of the parameter.
		 */
		public final String typeFQN;

		/**
		 * The name of the parameter.
		 */
		public final String name;

		/**
		 * The default value of the parameter.
		 * May be null, in which case the parameter is required.
		 */
		public final String defaultValue;

		/**
		 * The one and only constructor.
		 * @param typeFQN the FQN of the expected type of the parameter
		 * @param name the name of the parameter
		 * @param defaultValue the default value of the parameter, may be null if the parameter is required
		 */
		public Param(String typeFQN, String name, String defaultValue) {
			this.typeFQN = typeFQN;
			this.name = name;
			this.defaultValue = defaultValue;
		}
	}
}
