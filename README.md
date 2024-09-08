# Overview

This is a simple http(s) proxy to learn how forward proxies work, 
how communication with SSL is established and how to prove an SSL
endpoint oneself. It is based on [John Crickets Coding Challenges](https://codingchallenges.substack.com/p/coding-challenge-51-http-forward).

Everything is pure Java (which its batteries included), i.e. we
do not use any external libraries. On a side note, this is an ongoing
topic for me, i.e. for private playground projects let's not use any
external libraries but build everything one needs in the most pragmatic
way possible.

It's obviously not fully-featured or even useful in any way
except of understanding the basics of how an http proxy works.

## Building

    mvn clean package

## Running

    ./target/proxy

It will listen to http proxy requests on 8989 and https requests
on 8990.

## Demo

Connecting insecurely to an insecure server:
```
$ curl --proxy "http://localhost:8989" "http://httpbin.org/ip"
{
  "origin": "0:0:0:0:0:0:0:1, 37.201.165.202"
}
```
Connecting insecurely to a secure server:
```
$ curl --proxy "http://localhost:8989" "https://httpbin.org/ip"
{
  "origin": "37.201.165.202"
}
```
Connecting securely to a secure server:
```
$ curl --proxy-insecure --proxy "https://localhost:8990" "https://httpbin.org/ip"
{
  "origin": "37.201.165.202"
}
```
