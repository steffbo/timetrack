package auth

// Captured from the Java 25 / Spring Boot 4 production Docker reference.
// The loader suffix is JVM-specific; it is not an application contract.
const anonymousLogoutDetails = "class java.lang.String cannot be cast to class " +
	"cc.remer.timetrack.adapter.security.UserPrincipal (java.lang.String is in module " +
	"java.base of loader 'bootstrap'; cc.remer.timetrack.adapter.security.UserPrincipal " +
	"is in unnamed module of loader " +
	"org.springframework.boot.loader.launch.LaunchedClassLoader @4617c264)"
