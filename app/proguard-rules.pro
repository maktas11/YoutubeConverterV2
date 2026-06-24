# Personal sideload build runs with minification disabled, so these rules are
# inert for now. Kept so a future minified release wouldn't strip the library's
# reflectively-used model classes.
-keep class com.yausername.** { *; }
