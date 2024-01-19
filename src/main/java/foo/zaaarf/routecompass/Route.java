package foo.zaaarf.routecompass;

import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Internal representation of a REST route.
 */
public class Route {
	public final String classFqn;
	public final String route;
	public final String method;
	public final boolean deprecated;


	public Route(String classFqn, String route, RequestMethod[] methods, boolean deprecated) {
		this.classFqn = classFqn;
		this.route = route;
		StringBuilder methodStringBuilder = new StringBuilder("[");
		for(RequestMethod m : methods)
			methodStringBuilder
				.append(m.name())
				.append("|");
		methodStringBuilder
			.deleteCharAt(methodStringBuilder.length() - 1)
			.append("]");
		this.method = methodStringBuilder.toString();
		this.deprecated = deprecated;
	}
}
