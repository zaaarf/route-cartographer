package foo.zaaarf.routecompass;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class RouteCompass extends AbstractProcessor {

	private final HashMap<String, List<Route>> foundRoutes = new HashMap<>();
	private final HashSet<Class<? extends Annotation>> annotationClasses = new HashSet<>();

	public RouteCompass() {
		annotationClasses.add(RequestMapping.class);
		annotationClasses.add(GetMapping.class);
		annotationClasses.add(PostMapping.class);
		annotationClasses.add(PutMapping.class);
		annotationClasses.add(DeleteMapping.class);
		annotationClasses.add(PatchMapping.class);
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
		for(TypeElement annotationType : annotations) {
			env.getElementsAnnotatedWith(annotationType)
				.stream()
				.filter(elem -> elem instanceof ExecutableElement)
				.map(elem -> (ExecutableElement) elem)
				.forEach(elem -> {
					String classFQN = elem.getEnclosingElement().asType().toString();
					List<Route> routesInClass = foundRoutes.computeIfAbsent(classFQN, k -> new ArrayList<>());
					routesInClass.add(new Route(
						this.getFullRoute(annotationType, elem),
						this.getRequestMethods(annotationType, elem),
						this.getConsumedType(annotationType, elem),
						this.getProducedType(annotationType, elem),
						this.isDeprecated(elem),
						this.getParams(elem.getParameters())
					));
				});
		}

		try {
			FileObject serviceProvider = this.processingEnv.getFiler().createResource(
				StandardLocation.SOURCE_OUTPUT, "", "routes"
			);

			PrintWriter out = new PrintWriter(serviceProvider.openWriter());
			for(String componentClass : this.foundRoutes.keySet()) {
				out.println(componentClass + ":");

				List<Route> routesInClass = this.foundRoutes.get(componentClass);
				for(Route r : routesInClass) {
					out.print("\t- ");
					if(r.deprecated) out.print("[DEPRECATED] ");
					out.print(r.method + " " + r.route);
					if(r.consumes != null) out.print("(expects: " + r.consumes + ")");
					if(r.produces != null) out.print("(returns: " + r.produces + ")");
					out.println();

					for(Route.Param p : r.params) {
						out.print("\t\t- " + p.typeFQN + " " + p.name);
						if(p.defaultValue != null)
							out.print(" " + "(default: " + p.defaultValue + ")");
						out.println();
					}
				}
			}

			out.close();
		} catch(IOException e) {
			throw new RuntimeException(e);
		}

		return false; //don't claim them, let spring do its job
	}

	private String getFullRoute(TypeElement annotationType, Element element) {
		try {
			String route = this.getAnnotationFieldsValue(annotationType, element, "path", "value");
			return this.getParentOrFallback(element, route, (a, e) -> {
				String parent = this.getFullRoute(a, e);
				StringBuilder sb = new StringBuilder(parent);
				if(!parent.endsWith("/")) sb.append("/");
				sb.append(route);
				return sb.toString();
			});
		} catch (ReflectiveOperationException ex) {
			throw new RuntimeException(ex); //if it fails something went very wrong
		}
	}

	private MediaType getConsumedType(TypeElement annotationType, Element element) {
		try {
			MediaType res = this.getAnnotationFieldsValue(annotationType, element, "consumes");
			return res == null
				? this.getParentOrFallback(element, res, this::getConsumedType)
				: res;
		} catch(ReflectiveOperationException ex) {
			throw new RuntimeException(ex);
		}
	}

	private MediaType getProducedType(TypeElement annotationType, Element element) {
		try {
			MediaType res = this.getAnnotationFieldsValue(annotationType, element, "produces");
			return res == null
				? this.getParentOrFallback(element, res, this::getProducedType)
				: res;
		} catch(ReflectiveOperationException ex) {
			throw new RuntimeException(ex);
		}
	}

	private RequestMethod[] getRequestMethods(TypeElement annotationType, Element element) {
		RequestMethod[] methods = annotationType.getQualifiedName().contentEquals(RequestMapping.class.getName())
			? element.getAnnotation(RequestMapping.class).method()
			: annotationType.getAnnotation(RequestMapping.class).method();
		return methods.length == 0
			? this.getParentOrFallback(element, methods, this::getRequestMethods)
			: methods;
	}

	private boolean isDeprecated(Element elem) {
		return elem.getAnnotation(Deprecated.class) != null
			|| elem.getEnclosingElement().getAnnotation(Deprecated.class) != null;
	}

	private Route.Param[] getParams(List<? extends VariableElement> params) {
		return params.stream()
			.map(p -> {
				RequestParam ann = p.getAnnotation(RequestParam.class);
				if(ann == null) return null;

				String name = ann.name(); //first try annotation.name()
				name = name.isEmpty()
					? ann.value() //then annotation.value()
					: name;
				name = name.isEmpty()
					? p.getSimpleName().toString() //fall back on parameter name
					: name;

				String defaultValue = ann.defaultValue();
				if(defaultValue.equals(ValueConstants.DEFAULT_NONE))
					defaultValue = null;

				return new Route.Param(name, defaultValue, p.asType().toString());
			}).filter(Objects::nonNull).toArray(Route.Param[]::new);
	}

	@SuppressWarnings({"OptionalGetWithoutIsPresent", "unchecked"})
	private <T> T getAnnotationFieldsValue(TypeElement annotationType, Element element, String ... fieldNames)
		throws ReflectiveOperationException {

		Class<? extends Annotation> annClass = this.annotationClasses.stream()
			.filter(c -> annotationType.getQualifiedName().contentEquals(c.getName()))
			.findFirst()
			.get(); //should never fail

		T result = null;
		for(String fieldName : fieldNames) {
			result = (T) annClass.getField(fieldName).get(element.getAnnotation(annClass));
			if(result != null) return result;
		}

		return result;
	}

	private <T> T getParentOrFallback(Element element, T fallback, BiFunction<TypeElement, Element, T> fun) {
		List<Class<? extends Annotation>> found = this.annotationClasses.stream()
			.filter(annClass -> element.getEnclosingElement().getAnnotation(annClass) != null)
			.collect(Collectors.toList());

		if(found.isEmpty()) return fallback;

		if(found.size() > 1) this.processingEnv.getMessager().printMessage(
			Diagnostic.Kind.WARNING,
			"Found multiple mapping annotations on "
				+ element.getSimpleName().toString()
				+ ", only one of the will be considered!"
		);

		return fun.apply(
			this.processingEnv.getElementUtils()
				.getTypeElement(found.get(0).getName()),
			element
		);
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return annotationClasses.stream().map(Class::getCanonicalName).collect(Collectors.toSet());
	}
}
