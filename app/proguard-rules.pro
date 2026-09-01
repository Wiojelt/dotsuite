# Shizuku constructs the user service by class name in its privileged process.
-keep class io.github.wiojelt.dotsuite.service.UserService { *; }
-keep class io.github.wiojelt.dotsuite.IUserService { *; }
-keep class io.github.wiojelt.dotsuite.IUserService$* { *; }
