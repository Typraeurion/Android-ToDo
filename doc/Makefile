# Simple makefile for the documentation

.PHONY: all pdf html clean

all: pdf html

pdf: Building_ToDo.pdf ToDo.pdf

html: Building_ToDo/Building_ToDo.html ToDo/ToDo.html

Building_ToDo.pdf: Building_ToDo.texinfo
	texi2pdf Building_ToDo.texinfo

Building_ToDo:
	mkdir -p Building_ToDo

Building_ToDo/Building_ToDo.html: Building_ToDo.texinfo | Building_ToDo
	texi2html --split node Building_ToDo.texinfo
	touch $@

ToDo.pdf: ToDo.texinfo
	texi2pdf ToDo.texinfo

ToDo:
	mkdir -p ToDo

ToDo/ToDo.html: ToDo.texinfo | ToDo
	texi2html --split node ToDo.texinfo
	touch $@
