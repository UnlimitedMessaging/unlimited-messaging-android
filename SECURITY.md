# Security policy

This app holds SMS permissions and an access token to a messaging account.
Security reports are taken seriously.

## Reporting a vulnerability

Please **do not open a public issue**. Use GitHub's private vulnerability
reporting instead: on this repository, *Security > Report a vulnerability*.

Include what you found, how to reproduce it, and the app version. You will
get an answer within a few days.

## Scope

In scope: this app (token handling, the OAuth flow, exported components,
anything that could let another app or a network attacker send SMS, read
received SMS, or take over the session).

Vulnerabilities in the Unlimited Messaging API itself are welcome too, through
the same channel; they will be routed to the right team.

## Supported versions

Only the latest release receives fixes. Update before reporting.
