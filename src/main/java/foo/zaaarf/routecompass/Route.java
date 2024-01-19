package foo.zaaarf.routecompass;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Internal representation of a REST route.
 */
public class Route {
	public final String route;
	public final String method;
	public final String produces;
	public final String consumes;
	public final boolean deprecated;

	public Route(String route, RequestMethod[] methods, MediaType consumes, MediaType produces, boolean deprecated) {
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

		if(produces != null) this.produces = produces.toString();
		else this.produces = null;

		if(consumes != null) this.consumes = consumes.toString();
		else this.consumes = null;

		this.deprecated = deprecated;
	}
}
