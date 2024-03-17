package com.tcupiot.rftserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RftServerApplication

fun main(args: Array<String>) {
	runApplication<RftServerApplication>(*args)
}
