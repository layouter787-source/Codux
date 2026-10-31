cmake_minimum_required(VERSION 3.22.1)
project(codux_selfexe C)

add_library(codux-selfexe SHARED selfexe.c)
target_compile_options(codux-selfexe PRIVATE -Wall -U_FORTIFY_SOURCE -D_FORTIFY_SOURCE=0)
# Páginas de 16 KB: exigência da Google Play para apps que miram o Android 15+.
target_link_options(codux-selfexe PRIVATE -Wl,-z,max-page-size=16384)
target_link_libraries(codux-selfexe ${CMAKE_DL_LIBS})
