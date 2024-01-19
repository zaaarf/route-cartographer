package foo.zaaarf.routecompass;

import org.springframework.web.bind.annotation.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class RouteCompass extends AbstractProcessor {

	private final HashSet<Route> foundRoutes = new HashSet<>();
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
				.forEach(elem -> this.foundRoutes.add(new Route(
					elem.getEnclosingElement().asType().toString(),
					this.getFullRoute(annotationType, elem),
					this.getRequestMethods(annotationType, elem),
					this.isDeprecated(elem)
				)));
		}

		//TODO print

		return false; //don't claim them, let spring do its job
	}

	private String getFullRoute(TypeElement annotationType, Element element) {
		@SuppressWarnings("OptionalGetWithoutIsPresent") //find matching annotation class
		Class<? extends Annotation> annClass = this.annotationClasses.stream()
			.filter(c -> annotationType.getQualifiedName().contentEquals(c.getName()))
			.findFirst()
			.get(); //should never fail

		try {
			//it can be both path and value
			String pathValue = (String) annClass.getField("path").get(element.getAnnotation(annClass));
			String route = pathValue == null
				? (String) annClass.getField("value").get(element.getAnnotation(annClass))
				: pathValue;

			return this.parentOrFallback(element, route, (a, e) -> {
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

	private RequestMethod[] getRequestMethods(TypeElement annotationType, Element element) {
		RequestMethod[] methods = annotationType.getQualifiedName().contentEquals(RequestMapping.class.getName())
			? element.getAnnotation(RequestMapping.class).method()
			: annotationType.getAnnotation(RequestMapping.class).method();
		return methods.length == 0
			? this.parentOrFallback(element, methods, this::getRequestMethods)
			: methods;
	}

	private boolean isDeprecated(Element elem) {
		return elem.getAnnotation(Deprecated.class) != null
			|| elem.getEnclosingElement().getAnnotation(Deprecated.class) != null;
	}

	private <T> T parentOrFallback(Element element, T fallback, BiFunction<TypeElement, Element, T> fun) {
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
