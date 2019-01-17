/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pivotal.post;

import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * @author Rossen Stoyanchev
 */
public class PostMigrationConverterTests {

	private final PostMigrationConverter converter = new PostMigrationConverter(new StringWriter());


	@Test
	public void mentionsInListItems() {

		String body =
				"**[Adam McCormick](https://jira.spring.io/secure/ViewProfile.jspa?name=amccormick)** opened **[SPR-12237](https://jira.spring.io/browse/SPR-12237?redirect=false)** and commented\n" +
				"\n" +
				"I am trying to validate service layer method arguments using the `MethodValidationPostProcessor`.  This is failing on, what appears to be, different the method signatures.\n" +
				"\n" +
				"With an interface such as:\n" +
				"\n" +
				"```java\n" +
				"@Validated\n" +
				"public interface Service<T> {\n" +
				"\n" +
				"\tpublic T create(@Valid T item);\n" +
				"\t\n" +
				"\tpublic T update(String identifier, @Valid T item);\n" +
				"}\n" +
				"```\n" +
				"\n" +
				"And the following implementation:\n" +
				"\n" +
				"```java\n" +
				"@Named\n" +
				"public class ItemService implements Service<Item>{\n" +
				"\n" +
				"\t@Override\n" +
				"\tpublic Item create(Item item) {\n" +
				"\t\treturn item;\n" +
				"\t}\n" +
				"\n" +
				"\t@Override\n" +
				"\tpublic Item update(String identifier, Item item) {\n" +
				"\t\treturn item;\n" +
				"\t}\n" +
				"}\n" +
				"```\n" +
				"\n" +
				"Validation works fine when calling `update(String, Item)` but throws the following exception when calling `create(Item)`:\n" +
				"\n" +
				"```\n" +
				"java.lang.IllegalArgumentException: HV000162: The validated type org.commons.test.validation.ItemService does not specify the constructor/method: public abstract java.lang.Object org.commons.test.validation.Service.create(java.lang.Object)\n" +
				"```\n" +
				"\n" +
				"This is also working fine if `Service<T>` is an abstract class and I just extend it.\n" +
				"\n" +
				"Attached is a sample application that demonstrates the problem.\n" +
				"\n" +
				"\n" +
				"---\n" +
				"\n" +
				"**Affects:** 4.0.6\n" +
				"\n" +
				"**Attachments:**\n" +
				"- [validation.zip](https://jira.spring.io/secure/attachment/22214/validation.zip) (_7.17 kB_)\n" +
				"\n" +
				"**Referenced from:** commits https://github.com/spring-projects/spring-framework/commit/7118fcff0de2993d8654a2c72597aaa629e67e9b\n" +
				"\n" +
				"3 votes, 8 watchers\n";

		assertThat(converter.convert(body, new AtomicBoolean())).isEqualTo(
				"**[Adam McCormick](https://jira.spring.io/secure/ViewProfile.jspa?name=amccormick)** opened **[SPR-12237](https://jira.spring.io/browse/SPR-12237?redirect=false)** and commented\n" +
						"\n" +
						"I am trying to validate service layer method arguments using the `MethodValidationPostProcessor`.  This is failing on, what appears to be, different the method signatures.\n" +
						"\n" +
						"With an interface such as:\n" +
						"\n" +
						"```java\n" +
						"@Validated\n" +
						"public interface Service<T> {\n" +
						"\n" +
						"\tpublic T create(@Valid T item);\n" +
						"\t\n" +
						"\tpublic T update(String identifier, @Valid T item);\n" +
						"}\n" +
						"```\n" +
						"\n" +
						"And the following implementation:\n" +
						"\n" +
						"```java\n" +
						"@Named\n" +
						"public class ItemService implements Service<Item>{\n" +
						"\n" +
						"\t@Override\n" +
						"\tpublic Item create(Item item) {\n" +
						"\t\treturn item;\n" +
						"\t}\n" +
						"\n" +
						"\t@Override\n" +
						"\tpublic Item update(String identifier, Item item) {\n" +
						"\t\treturn item;\n" +
						"\t}\n" +
						"}\n" +
						"```\n" +
						"\n" +
						"Validation works fine when calling `update(String, Item)` but throws the following exception when calling `create(Item)`:\n" +
						"\n" +
						"```\n" +
						"java.lang.IllegalArgumentException: HV000162: The validated type org.commons.test.validation.ItemService does not specify the constructor/method: public abstract java.lang.Object org.commons.test.validation.Service.create(java.lang.Object)\n" +
						"```\n" +
						"\n" +
						"This is also working fine if `Service<T>` is an abstract class and I just extend it.\n" +
						"\n" +
						"Attached is a sample application that demonstrates the problem.\n" +
						"\n" +
						"---\n" +
						"\n" +
						"**Affects:** 4.0.6\n" +
						"\n" +
						"**Attachments:**\n" +
						"- [validation.zip](https://jira.spring.io/secure/attachment/22214/validation.zip) (_7.17 kB_)\n" +
						"\n" +
						"**Referenced from:** commits https://github.com/spring-projects/spring-framework/commit/7118fcff0de2993d8654a2c72597aaa629e67e9b\n" +
						"\n" +
						"3 votes, 8 watchers\n"
		);
	}

	@Test
	public void mentionsInListItemsNested() {

		String body =
				"Title:\n" +
				"- Something @Foo something else\n" +
				"  - Sub something @Bar blah\n" +
				"- Again @Baz and bazz\n" +
				"  - Sub something @Bar blah\n" +
   				"- Finally\n";

		assertThat(converter.convert(body, new AtomicBoolean())).isEqualTo(
				"Title:\n" +
				"- Something `@Foo` something else\n" +
				"  - Sub something `@Bar` blah\n" +
				"- Again `@Baz` and bazz\n" +
				"  - Sub something `@Bar` blah\n" +
				"- Finally\n\n");
	}

	@Test
	public void noChangeIfalreadyEscaped() {
		String body =
				"- ExceptionHandlerExceptionResolver started to log on WARN level #21916\n" +
						"- Clarify if `@DependsOn` influences bean destroy lifecycle ordering #21917\n" +
						"- MethodValidationPostProcessor still validates FactoryBean methods on CGLIB proxies #21919\n\n";

		assertThat(converter.convert(body, new AtomicBoolean())).isEqualTo(body);
	}

	@Test
	public void horizontalLineAfterText() {
		String body =
			"When consuming the [Nest Developer API](https://developers.nest.com/reference/api-overview) using `WebClient` I encounter an error on `keep-alive` events.\n" +
			"\n" +
			"I am unsure whether this is....\n" +
			"---------------------------------------------------------------------------------------------------------------------------------------------------------------\n" +
			"\n" +
			"The Nest Developer API in SSE send two types of messages:\n" +
			"1. data frames\n" +
			"2. keep alive frames";

		assertThat(converter.convert(body, new AtomicBoolean())).isEqualTo(
			"When consuming the [Nest Developer API](https://developers.nest.com/reference/api-overview) using `WebClient` I encounter an error on `keep-alive` events.\n" +
			"\n" +
			"I am unsure whether this is....\n\n" +
			"---\n" +
			"\n" +
			"The Nest Developer API in SSE send two types of messages:\n" +
			"1. data frames\n" +
			"2. keep alive frames\n\n");
	}

	@Test
	public void horizontal() {
		String body =
			"**[Sang-hyun Lee](https://jira.spring.io/secure/ViewProfile.jspa?name=zany)** opened **[SPR-12418](https://jira.spring.io/browse/SPR-12418?redirect=false)** and commented\n" +
					"\n" +
					"I'm using the Spring Framework libraries.\n" +
					"\n" +
					"--------------------------------------------------------------------------\n" +
					"\n" +
					"1. Spring Boot 1.1.8.RELEASE\n" +
					"   1-1. org.springframework.boot:spring-boot-starter-amqp:jar:1.1.8.RELEASE\n" +
					"   1-2. org.springframework.boot:spring-boot-starter-websocket:jar:1.1.8.RELEASE\n" +
					"2. org.springframework:spring-messaging:jar:4.0.7.RELEASE\n" +
					"3. org.projectreactor:reactor-net:jar:1.1.4.RELEASE (for StompBrokerRelay)\n" +
					"\n" +
					"--------------------------------------------------------------------------\n" +
					"\n" +
					"I referneced http://assets.spring.io/wp/WebSocketBlogPost.html\n" +
					"but I'm using the \"Apache ActiveMQ 5.10.0\" and configured Stomp Broker Relay.\n" +
					"\n" +
					"My Application publishing stomp messages to client 5 ~ 30 messages per second.\n" +
					"each message has 300 ~ 700 bytes length of payload.\n" +
					"\n" +
					"I meet \"StringIndexOutOfBoundsException\" irregularly.\n" +
					"and after connection is closed.\n" +
					"\n" +
					"stacktrace is below.\n" +
					"\n" +
					"--------------------------------------------------------------------------\n" +
					"\n" +
					"java.lang.StringIndexOutOfBoundsException: String index out of range: 3\n" +
					"at java.lang.String.charAt(String.java:658)\n" +
					"at org.springframework.messaging.simp.stomp.StompDecoder.unescape(StompDecoder.java:221)\n" +
					"at org.springframework.messaging.simp.stomp.StompDecoder.readHeaders(StompDecoder.java:197)\n" +
					"at org.springframework.messaging.simp.stomp.StompDecoder.decodeMessage(StompDecoder.java:123)\n" +
					"at org.springframework.messaging.simp.stomp.StompDecoder.decode(StompDecoder.java:99)\n" +
					"at org.springframework.messaging.simp.stomp.StompDecoder.decode(StompDecoder.java:68)\n" +
					"at org.springframework.messaging.simp.stomp.Reactor11StompCodec$DecodingFunction.apply(Reactor11StompCodec.java:96)\n" +
					"at org.springframework.messaging.simp.stomp.Reactor11StompCodec$DecodingFunction.apply(Reactor11StompCodec.java:83)\n" +
					"at reactor.net.AbstractNetChannel.read(AbstractNetChannel.java:214)\n" +
					"at reactor.net.netty.NettyNetChannelInboundHandler.passToConnection(NettyNetChannelInboundHandler.java:105)\n" +
					"at reactor.net.netty.NettyNetChannelInboundHandler.channelRead(NettyNetChannelInboundHandler.java:69)\n" +
					"at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:332)\n" +
					"at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:318)\n" +
					"at io.netty.channel.DefaultChannelPipeline.fireChannelRead(DefaultChannelPipeline.java:787)\n" +
					"at io.netty.channel.nio.AbstractNioByteChannel$NioByteUnsafe.read(AbstractNioByteChannel.java:125)\n" +
					"at io.netty.channel.nio.NioEventLoop.processSelectedKey(NioEventLoop.java:507)\n" +
					"at io.netty.channel.nio.NioEventLoop.processSelectedKeysOptimized(NioEventLoop.java:464)\n" +
					"at io.netty.channel.nio.NioEventLoop.processSelectedKeys(NioEventLoop.java:378)\n" +
					"at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:350)\n" +
					"at io.netty.util.concurrent.SingleThreadEventExecutor$2.run(SingleThreadEventExecutor.java:116)\n" +
					"at java.lang.Thread.run(Thread.java:745)\n" +
					"------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n" +
					"\n" +
					"so, I debug the StompDecoder.java (4.0.7.RELEASE source)\n" +
					"\n" +
					"and I found out the StompDecoder.unescape() method throws the StringIndexOutOfBoundsException\n" +
					"when message header value end with \"\\\". (in my case \"message-id\" value exactly)\n" +
					"\n" +
					"Messages that caused the exception are like below.\n" +
					"\n" +
					"CASE #1)\n" +
					"--------\n" +
					"\n" +
					"content-type:application/json;charset=UTF-8\n" +
					"message-id:ID\\\n" +
					"095041.588 ERROR 20569 --- [eactor-tcp-io-4] reactor.core.Reactor                     : String index out of range: 3\n" +
					"java.lang.StringIndexOutOfBoundsException: String index out of range: 3\n" +
					"at java.lang.String.charAt(String.java:658)\n" +
					"at org.springframework.messaging.simp.stomp.StompDecoder.unescape(StompDecoder.java:221)\n" +
					"at org.springframework.messaging.simp.stomp.StompDecoder.readHeaders(StompDecoder.java:197)\n" +
					"at org.springframework.messaging.simp.stomp.StompDecoder.decodeMessage(StompDecoder.java:123)\n" +
					"\n" +
					"... same stack trace...\n" +
					"\n" +
					"CASE #2)\n" +
					"--------\n" +
					"\n" +
					"content-type:application/json;charset=UTF-8\n" +
					"message-id:ID\\cktpdevGW-39005-1415095980735-2\\c882\\c-1\\c1\\\n" +
					"095056.596 ERROR 20569 --- [eactor-tcp-io-3] reactor.core.Reactor                     : String index out of range: 47\n" +
					"java.lang.StringIndexOutOfBoundsException: String index out of range: 47\n" +
					"at java.lang.String.charAt(String.java:658)\n" +
					"at org.springframework.messaging.simp.stomp.StompDecoder.unescape(StompDecoder.java:221)\n" +
					"at org.springframework.messaging.simp.stomp.StompDecoder.readHeaders(StompDecoder.java:197)\n" +
					"at org.springframework.messaging.simp.stomp.StompDecoder.decodeMessage(StompDecoder.java:123)\n" +
					"\n" +
					"... same stack trace...\n" +
					"\n" +
					"CASE #3)\n" +
					"--------\n" +
					"\n" +
					"content-type:application/json;charset=UTF-8\n" +
					"message-id:ID\\cktpdevGW-39005-1415095980735-2\\c882\\\n" +
					"101513.253 ERROR 20569 --- [eactor-tcp-io-4] reactor.core.Reactor                     : String index out of range: 40\n" +
					"java.lang.StringIndexOutOfBoundsException: String index out of range: 40\n" +
					"at java.lang.String.charAt(String.java:658)\n" +
					"at org.springframework.messaging.simp.stomp.StompDecoder.unescape(StompDecoder.java:221)\n" +
					"at org.springframework.messaging.simp.stomp.StompDecoder.readHeaders(StompDecoder.java:197)\n" +
					"at org.springframework.messaging.simp.stomp.StompDecoder.decodeMessage(StompDecoder.java:123)\n" +
					"\n" +
					"... same stack trace...\n" +
					"\n" +
					"every case of messages are ended with \"\\\" character.\n" +
					"that messages are looks like incomplete message from the network.\n" +
					"\n" +
					"this exception happens on localhost (127.0.0.1 loopback)\n" +
					"\n" +
					"---\n" +
					"\n" +
					"**Affects:** 4.0.7\n" +
					"\n" +
					"**Issue Links:**\n" +
					"- #17995 StompDecoder fails for partial headers containing escape sequence\n" +
					"\n" +
					"**Referenced from:** commits https://github.com/spring-projects/spring-framework/commit/18033486aec5de46833a2437026a0494c6485460, https://github.com/spring-projects/spring-framework/commit/b331d6501948b9fb4b27027cabba15a662fe031a\n" +
					"\n" +
					"**Backported to:** [4.0.8](https://github.com/spring-projects/spring-framework/milestone/120?closed=1)\n";

		String actual = converter.convert(body, new AtomicBoolean());
		assertThat(actual).isEqualTo(
"**[Sang-hyun Lee](https://jira.spring.io/secure/ViewProfile.jspa?name=zany)** opened **[SPR-12418](https://jira.spring.io/browse/SPR-12418?redirect=false)** and commented\n" +
		"\n" +
		"I'm using the Spring Framework libraries.\n" +
		"\n" +
		"---\n" +
		"\n" +
		"1. Spring Boot 1.1.8.RELEASE\n" +
		"   1-1. org.springframework.boot:spring-boot-starter-amqp:jar:1.1.8.RELEASE\n" +
		"   1-2. org.springframework.boot:spring-boot-starter-websocket:jar:1.1.8.RELEASE\n" +
		"2. org.springframework:spring-messaging:jar:4.0.7.RELEASE\n" +
		"3. org.projectreactor:reactor-net:jar:1.1.4.RELEASE (for StompBrokerRelay)\n" +
		"\n" +
		"---\n" +
		"\n" +
		"I referneced http://assets.spring.io/wp/WebSocketBlogPost.html\n" +
		"but I'm using the \"Apache ActiveMQ 5.10.0\" and configured Stomp Broker Relay.\n" +
		"\n" +
		"My Application publishing stomp messages to client 5 ~ 30 messages per second.\n" +
		"each message has 300 ~ 700 bytes length of payload.\n" +
		"\n" +
		"I meet \"StringIndexOutOfBoundsException\" irregularly.\n" +
		"and after connection is closed.\n" +
		"\n" +
		"stacktrace is below.\n" +
		"\n" +
		"---\n" +
		"\n" +
		"java.lang.StringIndexOutOfBoundsException: String index out of range: 3\n" +
		"at java.lang.String.charAt(String.java:658)\n" +
		"at org.springframework.messaging.simp.stomp.StompDecoder.unescape(StompDecoder.java:221)\n" +
		"at org.springframework.messaging.simp.stomp.StompDecoder.readHeaders(StompDecoder.java:197)\n" +
		"at org.springframework.messaging.simp.stomp.StompDecoder.decodeMessage(StompDecoder.java:123)\n" +
		"at org.springframework.messaging.simp.stomp.StompDecoder.decode(StompDecoder.java:99)\n" +
		"at org.springframework.messaging.simp.stomp.StompDecoder.decode(StompDecoder.java:68)\n" +
		"at org.springframework.messaging.simp.stomp.Reactor11StompCodec$DecodingFunction.apply(Reactor11StompCodec.java:96)\n" +
		"at org.springframework.messaging.simp.stomp.Reactor11StompCodec$DecodingFunction.apply(Reactor11StompCodec.java:83)\n" +
		"at reactor.net.AbstractNetChannel.read(AbstractNetChannel.java:214)\n" +
		"at reactor.net.netty.NettyNetChannelInboundHandler.passToConnection(NettyNetChannelInboundHandler.java:105)\n" +
		"at reactor.net.netty.NettyNetChannelInboundHandler.channelRead(NettyNetChannelInboundHandler.java:69)\n" +
		"at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:332)\n" +
		"at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:318)\n" +
		"at io.netty.channel.DefaultChannelPipeline.fireChannelRead(DefaultChannelPipeline.java:787)\n" +
		"at io.netty.channel.nio.AbstractNioByteChannel$NioByteUnsafe.read(AbstractNioByteChannel.java:125)\n" +
		"at io.netty.channel.nio.NioEventLoop.processSelectedKey(NioEventLoop.java:507)\n" +
		"at io.netty.channel.nio.NioEventLoop.processSelectedKeysOptimized(NioEventLoop.java:464)\n" +
		"at io.netty.channel.nio.NioEventLoop.processSelectedKeys(NioEventLoop.java:378)\n" +
		"at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:350)\n" +
		"at io.netty.util.concurrent.SingleThreadEventExecutor$2.run(SingleThreadEventExecutor.java:116)\n" +
		"at java.lang.Thread.run(Thread.java:745)\n\n" +
		"---\n" +
		"\n" +
		"so, I debug the StompDecoder.java (4.0.7.RELEASE source)\n" +
		"\n" +
		"and I found out the StompDecoder.unescape() method throws the StringIndexOutOfBoundsException\n" +
		"when message header value end with \"\\\". (in my case \"message-id\" value exactly)\n" +
		"\n" +
		"Messages that caused the exception are like below.\n" +
		"\n" +
		"CASE #1)\n\n" +
		"---\n" +
		"\n" +
		"content-type:application/json;charset=UTF-8\n" +
		"message-id:ID\\\n" +
		"095041.588 ERROR 20569 --- [eactor-tcp-io-4] reactor.core.Reactor                     : String index out of range: 3\n" +
		"java.lang.StringIndexOutOfBoundsException: String index out of range: 3\n" +
		"at java.lang.String.charAt(String.java:658)\n" +
		"at org.springframework.messaging.simp.stomp.StompDecoder.unescape(StompDecoder.java:221)\n" +
		"at org.springframework.messaging.simp.stomp.StompDecoder.readHeaders(StompDecoder.java:197)\n" +
		"at org.springframework.messaging.simp.stomp.StompDecoder.decodeMessage(StompDecoder.java:123)\n" +
		"\n" +
		"... same stack trace...\n" +
		"\n" +
		"CASE #2)\n\n" +
		"---\n" +
		"\n" +
		"content-type:application/json;charset=UTF-8\n" +
		"message-id:ID\\cktpdevGW-39005-1415095980735-2\\c882\\c-1\\c1\\\n" +
		"095056.596 ERROR 20569 --- [eactor-tcp-io-3] reactor.core.Reactor                     : String index out of range: 47\n" +
		"java.lang.StringIndexOutOfBoundsException: String index out of range: 47\n" +
		"at java.lang.String.charAt(String.java:658)\n" +
		"at org.springframework.messaging.simp.stomp.StompDecoder.unescape(StompDecoder.java:221)\n" +
		"at org.springframework.messaging.simp.stomp.StompDecoder.readHeaders(StompDecoder.java:197)\n" +
		"at org.springframework.messaging.simp.stomp.StompDecoder.decodeMessage(StompDecoder.java:123)\n" +
		"\n" +
		"... same stack trace...\n" +
		"\n" +
		"CASE #3)\n\n" +
		"---\n" +
		"\n" +
		"content-type:application/json;charset=UTF-8\n" +
		"message-id:ID\\cktpdevGW-39005-1415095980735-2\\c882\\\n" +
		"101513.253 ERROR 20569 --- [eactor-tcp-io-4] reactor.core.Reactor                     : String index out of range: 40\n" +
		"java.lang.StringIndexOutOfBoundsException: String index out of range: 40\n" +
		"at java.lang.String.charAt(String.java:658)\n" +
		"at org.springframework.messaging.simp.stomp.StompDecoder.unescape(StompDecoder.java:221)\n" +
		"at org.springframework.messaging.simp.stomp.StompDecoder.readHeaders(StompDecoder.java:197)\n" +
		"at org.springframework.messaging.simp.stomp.StompDecoder.decodeMessage(StompDecoder.java:123)\n" +
		"\n" +
		"... same stack trace...\n" +
		"\n" +
		"every case of messages are ended with \"\\\" character.\n" +
		"that messages are looks like incomplete message from the network.\n" +
		"\n" +
		"this exception happens on localhost (127.0.0.1 loopback)\n" +
		"\n" +
		"---\n" +
		"\n" +
		"**Affects:** 4.0.7\n" +
		"\n" +
		"**Issue Links:**\n" +
		"- #17995 StompDecoder fails for partial headers containing escape sequence\n" +
		"\n" +
		"**Referenced from:** commits https://github.com/spring-projects/spring-framework/commit/18033486aec5de46833a2437026a0494c6485460, https://github.com/spring-projects/spring-framework/commit/b331d6501948b9fb4b27027cabba15a662fe031a\n" +
		"\n" +
		"**Backported to:** [4.0.8](https://github.com/spring-projects/spring-framework/milestone/120?closed=1)\n"		);
	}

	@Test
	public void horizontal2() {
		String body =
				"**[Dave Knipp](https://jira.spring.io/secure/ViewProfile.jspa?name=puppetmasta)** opened **[SPR-2908](https://jira.spring.io/browse/SPR-2908?redirect=false)** and commented\n" +
						"\n" +
						"I get an exception when I define a pointcut via `@Pointcut` and I define the 'args(...)' in that pointcut.  I then apply the defined pointcut with `@Around` advice and I receive the following exeception :\n" +
						"\n" +
						"==================================================================================\n" +
						"Caused by: java.lang.IllegalStateException: Failed to bind all argument names: 1 argument(s) could not be bound\n" +
						"at org.springframework.aop.aspectj.AspectJAdviceParameterNameDiscoverer.getParameterNames(AspectJAdviceParameterNameDiscoverer.java:282)\n" +
						"at org.springframework.core.PrioritizedParameterNameDiscoverer.getParameterNames(PrioritizedParameterNameDiscoverer.java:54)\n" +
						"at org.springframework.aop.aspectj.AbstractAspectJAdvice.bindArgumentsByName(AbstractAspectJAdvice.java:356)\n" +
						"at org.springframework.aop.aspectj.AbstractAspectJAdvice.calculateArgumentBindings(AbstractAspectJAdvice.java:317)\n" +
						"at org.springframework.aop.aspectj.AbstractAspectJAdvice.afterPropertiesSet(AbstractAspectJAdvice.java:283)\n" +
						"at org.springframework.aop.aspectj.annotation.ReflectiveAspectJAdvisorFactory.getAdvice(ReflectiveAspectJAdvisorFactory.java:211)\n" +
						"... 172 more\n" +
						"===================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================================\n" +
						"\n" +
						"I stepped through the code and found that org.springframework.aop.aspectj.AspectJAdviceParameterNameDiscoverer.maybeBindThisOrTargetOrArgsFromPointcutExpression() is only binding arguments if the 'args(...)' declaration is defined in the advice (`@Around`) declaration as opposed to being defined in the `@Pointcut` declaration.\n" +
						"\n" +
						"Defining the 'args(...)' in the `@Pointcut` declaration is valid according to the aspectj documentation for the new annotations in AspectJ 5. (see http://www.eclipse.org/aspectj/doc/released/adk15notebook/ataspectj-pcadvice.html for an example)\n" +
						"\n" +
						"The related forum thread explaining my findings and example can be found here : http://forum.springframework.org/showthread.php?t=32111\n" +
						"\n" +
						"---\n" +
						"\n" +
						"**Affects:** 2.0.1\n";

		String actual = converter.convert(body, new AtomicBoolean());
		assertThat(actual).isEqualTo(
				"**[Dave Knipp](https://jira.spring.io/secure/ViewProfile.jspa?name=puppetmasta)** opened **[SPR-2908](https://jira.spring.io/browse/SPR-2908?redirect=false)** and commented\n" +
						"\n" +
						"I get an exception when I define a pointcut via `@Pointcut` and I define the 'args(...)' in that pointcut.  I then apply the defined pointcut with `@Around` advice and I receive the following exeception :\n" +
						"\n" +
						"---\n\n" +
						"Caused by: java.lang.IllegalStateException: Failed to bind all argument names: 1 argument(s) could not be bound\n" +
						"at org.springframework.aop.aspectj.AspectJAdviceParameterNameDiscoverer.getParameterNames(AspectJAdviceParameterNameDiscoverer.java:282)\n" +
						"at org.springframework.core.PrioritizedParameterNameDiscoverer.getParameterNames(PrioritizedParameterNameDiscoverer.java:54)\n" +
						"at org.springframework.aop.aspectj.AbstractAspectJAdvice.bindArgumentsByName(AbstractAspectJAdvice.java:356)\n" +
						"at org.springframework.aop.aspectj.AbstractAspectJAdvice.calculateArgumentBindings(AbstractAspectJAdvice.java:317)\n" +
						"at org.springframework.aop.aspectj.AbstractAspectJAdvice.afterPropertiesSet(AbstractAspectJAdvice.java:283)\n" +
						"at org.springframework.aop.aspectj.annotation.ReflectiveAspectJAdvisorFactory.getAdvice(ReflectiveAspectJAdvisorFactory.java:211)\n" +
						"... 172 more\n\n" +
						"---\n" +
						"\n" +
						"I stepped through the code and found that org.springframework.aop.aspectj.AspectJAdviceParameterNameDiscoverer.maybeBindThisOrTargetOrArgsFromPointcutExpression() is only binding arguments if the 'args(...)' declaration is defined in the advice (`@Around`) declaration as opposed to being defined in the `@Pointcut` declaration.\n" +
						"\n" +
						"Defining the 'args(...)' in the `@Pointcut` declaration is valid according to the aspectj documentation for the new annotations in AspectJ 5. (see http://www.eclipse.org/aspectj/doc/released/adk15notebook/ataspectj-pcadvice.html for an example)\n" +
						"\n" +
						"The related forum thread explaining my findings and example can be found here : http://forum.springframework.org/showthread.php?t=32111\n" +
						"\n" +
						"---\n" +
						"\n" +
						"**Affects:** 2.0.1\n"
		);
	}

	@Test
	public void horizontalInCode() {
		String body =
"**[Fedor Bobin](https://jira.spring.io/secure/ViewProfile.jspa?name=fuud)** commented\n" +
		"\n" +
		"I have a fix.\n" +
		"I will not have access to GitHub till Monday. Then I will make a PR with this patch:\n" +
		"\n" +
		"```\n" +
		"Index: spring-jdbc/src/main/java/org/springframework/jdbc/core/PreparedStatementCreatorFactory.java\n" +
		"IDEA additional info:\n" +
		"Subsystem: com.intellij.openapi.diff.impl.patch.CharsetEP\n" +
		"<+>UTF-8\n" +
		"===================================================================\n" +
		"--- spring-jdbc/src/main/java/org/springframework/jdbc/core/PreparedStatementCreatorFactory.java\t(revision aa656c47b87b75f5bf28f864c28d8a220ac25fbc)\n" +
		"+++ spring-jdbc/src/main/java/org/springframework/jdbc/core/PreparedStatementCreatorFactory.java\t(revision 475e623883a6f07419242b54d61a8f6758b9fe7a)\n" +
		"@@ -176,6 +176,9 @@\n" +
		" \t\t\t\tsqlToUse, params != null ? Arrays.asList(params) : Collections.emptyList());\n" +
		" \t}\n" +
		" \n" +
		"+\tpublic String getSql() {\n" +
		"+\t\treturn sql;\n" +
		"+\t}\n" +
		" \n" +
		" \t/**\n" +
		" \t * PreparedStatementCreator implementation returned by this class.\n" +
		"Index: spring-jdbc/src/main/java/org/springframework/jdbc/core/namedparam/NamedParameterBatchUpdateUtils.java\n" +
		"IDEA additional info:\n" +
		"Subsystem: com.intellij.openapi.diff.impl.patch.CharsetEP\n" +
		"<+>UTF-8\n" +
		"===================================================================\n" +
		"--- spring-jdbc/src/main/java/org/springframework/jdbc/core/namedparam/NamedParameterBatchUpdateUtils.java\t(revision aa656c47b87b75f5bf28f864c28d8a220ac25fbc)\n" +
		"+++ spring-jdbc/src/main/java/org/springframework/jdbc/core/namedparam/NamedParameterBatchUpdateUtils.java\t(revision 475e623883a6f07419242b54d61a8f6758b9fe7a)\n" +
		"@@ -22,6 +22,8 @@\n" +
		" import org.springframework.jdbc.core.BatchPreparedStatementSetter;\n" +
		" import org.springframework.jdbc.core.BatchUpdateUtils;\n" +
		" import org.springframework.jdbc.core.JdbcOperations;\n" +
		"+import org.springframework.jdbc.core.PreparedStatementCreatorFactory;\n" +
		"+import org.springframework.jdbc.core.PreparedStatementSetter;\n" +
		" \n" +
		" /**\n" +
		"  * Generic utility methods for working with JDBC batch statements using named parameters.\n" +
		"@@ -32,22 +34,17 @@\n" +
		"  */\n" +
		" public class NamedParameterBatchUpdateUtils extends BatchUpdateUtils {\n" +
		" \n" +
		"-\tpublic static int[] executeBatchUpdateWithNamedParameters(final ParsedSql parsedSql,\n" +
		"-\t\t\tfinal SqlParameterSource[] batchArgs, JdbcOperations jdbcOperations) {\n" +
		"+\tpublic static int[] executeBatchUpdateWithNamedParameters(ParsedSql parsedSql, final PreparedStatementCreatorFactory pscf,\n" +
		"+\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t  final SqlParameterSource[] batchArgs, JdbcOperations jdbcOperations) {\n" +
		" \n" +
		"-\t\tif (batchArgs.length <= 0) {\n" +
		"-\t\t\treturn new int[] {0};\n" +
		"-\t\t}\n" +
		"-\n" +
		"-\t\tString sqlToUse = NamedParameterUtils.substituteNamedParameters(parsedSql, batchArgs[0]);\n" +
		" \t\treturn jdbcOperations.batchUpdate(\n" +
		"-\t\t\t\tsqlToUse,\n" +
		"+\t\t\t\tpscf.getSql(),\n" +
		" \t\t\t\tnew BatchPreparedStatementSetter() {\n" +
		" \t\t\t\t\t@Override\n" +
		" \t\t\t\t\tpublic void setValues(PreparedStatement ps, int i) throws SQLException {\n" +
		" \t\t\t\t\t\tObject[] values = NamedParameterUtils.buildValueArray(parsedSql, batchArgs[i], null);\n" +
		"-\t\t\t\t\t\tint[] columnTypes = NamedParameterUtils.buildSqlTypeArray(parsedSql, batchArgs[i]);\n" +
		"-\t\t\t\t\t\tsetStatementParameters(values, ps, columnTypes);\n" +
		"+\t\t\t\t\t\tPreparedStatementSetter preparedStatementSetter = pscf.newPreparedStatementSetter(values);\n" +
		"+\t\t\t\t\t\tpreparedStatementSetter.setValues(ps);\n" +
		" \t\t\t\t\t}\n" +
		" \t\t\t\t\t@Override\n" +
		" \t\t\t\t\tpublic int getBatchSize() {\n" +
		"Index: spring-jdbc/src/main/java/org/springframework/jdbc/core/namedparam/NamedParameterJdbcTemplate.java\n" +
		"IDEA additional info:\n" +
		"Subsystem: com.intellij.openapi.diff.impl.patch.CharsetEP\n" +
		"<+>UTF-8\n" +
		"===================================================================\n" +
		"--- spring-jdbc/src/main/java/org/springframework/jdbc/core/namedparam/NamedParameterJdbcTemplate.java\t(revision aa656c47b87b75f5bf28f864c28d8a220ac25fbc)\n" +
		"+++ spring-jdbc/src/main/java/org/springframework/jdbc/core/namedparam/NamedParameterJdbcTemplate.java\t(revision 475e623883a6f07419242b54d61a8f6758b9fe7a)\n" +
		"@@ -352,8 +352,13 @@\n" +
		" \n" +
		" \t@Override\n" +
		" \tpublic int[] batchUpdate(String sql, SqlParameterSource[] batchArgs) {\n" +
		"+\t\tif (batchArgs.length <= 0) {\n" +
		"+\t\t\treturn new int[] {0};\n" +
		"+\t\t}\n" +
		"+\t\tParsedSql parsedSql = getParsedSql(sql);\n" +
		"+\t\tPreparedStatementCreatorFactory pscf = getPreparedStatementCreatorFactory(parsedSql, batchArgs[0], null);\n" +
		" \t\treturn NamedParameterBatchUpdateUtils.executeBatchUpdateWithNamedParameters(\n" +
		"-\t\t\t\tgetParsedSql(sql), batchArgs, getJdbcOperations());\n" +
		"+\t\t\t\tparsedSql, pscf, batchArgs, getJdbcOperations());\n" +
		" \t}\n" +
		" \n" +
		" \n" +
		"@@ -389,14 +394,20 @@\n" +
		" \t\t\t@Nullable Consumer<PreparedStatementCreatorFactory> customizer) {\n" +
		" \n" +
		" \t\tParsedSql parsedSql = getParsedSql(sql);\n" +
		"+\t\tPreparedStatementCreatorFactory pscf = getPreparedStatementCreatorFactory(parsedSql, paramSource, customizer);\n" +
		"+\t\tObject[] params = NamedParameterUtils.buildValueArray(parsedSql, paramSource, null);\n" +
		"+\t\treturn pscf.newPreparedStatementCreator(params);\n" +
		"+\t}\n" +
		"+\n" +
		"+\tprotected PreparedStatementCreatorFactory getPreparedStatementCreatorFactory(ParsedSql parsedSql, SqlParameterSource paramSource,\n" +
		"+\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t @Nullable Consumer<PreparedStatementCreatorFactory> customizer){\n" +
		" \t\tString sqlToUse = NamedParameterUtils.substituteNamedParameters(parsedSql, paramSource);\n" +
		" \t\tList<SqlParameter> declaredParameters = NamedParameterUtils.buildSqlParameterList(parsedSql, paramSource);\n" +
		" \t\tPreparedStatementCreatorFactory pscf = new PreparedStatementCreatorFactory(sqlToUse, declaredParameters);\n" +
		" \t\tif (customizer != null) {\n" +
		" \t\t\tcustomizer.accept(pscf);\n" +
		" \t\t}\n" +
		"-\t\tObject[] params = NamedParameterUtils.buildValueArray(parsedSql, paramSource, null);\n" +
		"-\t\treturn pscf.newPreparedStatementCreator(params);\n" +
		"+\t\treturn pscf;\n" +
		" \t}\n" +
		" \n" +
		" \t/**\n" +
		"Index: spring-jdbc/src/test/java/org/springframework/jdbc/core/namedparam/NamedParameterJdbcTemplateTests.java\n" +
		"IDEA additional info:\n" +
		"Subsystem: com.intellij.openapi.diff.impl.patch.CharsetEP\n" +
		"<+>UTF-8\n" +
		"===================================================================\n" +
		"--- spring-jdbc/src/test/java/org/springframework/jdbc/core/namedparam/NamedParameterJdbcTemplateTests.java\t(revision aa656c47b87b75f5bf28f864c28d8a220ac25fbc)\n" +
		"+++ spring-jdbc/src/test/java/org/springframework/jdbc/core/namedparam/NamedParameterJdbcTemplateTests.java\t(revision 475e623883a6f07419242b54d61a8f6758b9fe7a)\n" +
		"@@ -36,6 +36,7 @@\n" +
		" import org.junit.Test;\n" +
		" import org.junit.rules.ExpectedException;\n" +
		" \n" +
		"+import org.mockito.InOrder;\n" +
		" import org.springframework.jdbc.Customer;\n" +
		" import org.springframework.jdbc.core.JdbcOperations;\n" +
		" import org.springframework.jdbc.core.JdbcTemplate;\n" +
		"@@ -460,6 +461,41 @@\n" +
		" \t\tverify(preparedStatement, atLeastOnce()).close();\n" +
		" \t\tverify(connection, atLeastOnce()).close();\n" +
		" \t}\n" +
		"+\n" +
		"+\t@Test\n" +
		"+\tpublic void testBatchUpdateWithInClause() throws Exception {\n" +
		"+\t\t@SuppressWarnings(\"unchecked\")\n" +
		"+\t\tMap<String, Object>[] parameters = new Map[2];\n" +
		"+\t\tparameters[0] = Collections.singletonMap(\"ids\", Arrays.asList(1, 2));\n" +
		"+\t\tparameters[1] = Collections.singletonMap(\"ids\", Arrays.asList(3, 4));\n" +
		"+\n" +
		"+\t\tfinal int[] rowsAffected = new int[] {1, 2};\n" +
		"+\t\tgiven(preparedStatement.executeBatch()).willReturn(rowsAffected);\n" +
		"+\t\tgiven(connection.getMetaData()).willReturn(databaseMetaData);\n" +
		"+\n" +
		"+\t\tJdbcTemplate template = new JdbcTemplate(dataSource, false);\n" +
		"+\t\tnamedParameterTemplate = new NamedParameterJdbcTemplate(template);\n" +
		"+\n" +
		"+\t\tint[] actualRowsAffected = namedParameterTemplate.batchUpdate(\n" +
		"+\t\t\t\t\"delete sometable where id in (:ids)\",\n" +
		"+\t\t\t\tparameters\n" +
		"+\t\t);\n" +
		"+\n" +
		"+\t\tassertEquals(\"executed 2 updates\", 2, actualRowsAffected.length);\n" +
		"+\n" +
		"+\t\tInOrder inOrder = inOrder(preparedStatement);\n" +
		"+\n" +
		"+\t\tinOrder.verify(preparedStatement).setObject(1, 1);\n" +
		"+\t\tinOrder.verify(preparedStatement).setObject(2, 2);\n" +
		"+\t\tinOrder.verify(preparedStatement).addBatch();\n" +
		"+\n" +
		"+\t\tinOrder.verify(preparedStatement).setObject(1, 3);\n" +
		"+\t\tinOrder.verify(preparedStatement).setObject(2, 4);\n" +
		"+\t\tinOrder.verify(preparedStatement).addBatch();\n" +
		"+\n" +
		"+\t\tinOrder.verify(preparedStatement, atLeastOnce()).close();\n" +
		"+\t\tverify(connection, atLeastOnce()).close();\n" +
		"+\t}\n" +
		" \n" +
		" \t@Test\n" +
		" \tpublic void testBatchUpdateWithSqlParameterSourcePlusTypeInfo() throws Exception {\n" +
		"\n" +
		"```\n" +
		"\n";

		assertThat(converter.convert(body, new AtomicBoolean())).isEqualTo(body);
	}

	@Test
	public void escapedEmphasis() {
		String body =
				"In other words `\"\\{var\\}\"` where `var` is equal to `\"\\{!geofilt\\}\"`.\n" +
				"representation `\\{\"complaint\":\\{\"claim\":\"1\"\\},\"version\":\\{\"expression\":\"mld\",\"actionTaken\":\"mld\"\\}\\`}.\n";

		assertThat(converter.convert(body, new AtomicBoolean())).isEqualTo(
				"In other words `\"{var}\"` where `var` is equal to `\"{!geofilt}\"`.\n" +
				"representation `{\"complaint\":{\"claim\":\"1\"},\"version\":{\"expression\":\"mld\",\"actionTaken\":\"mld\"}}`.\n");
	}

}
