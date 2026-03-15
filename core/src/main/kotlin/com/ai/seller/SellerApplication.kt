package com.ai.seller

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class SellerApplication

fun main(args: Array<String>) {
	runApplication<SellerApplication>(*args)
}
