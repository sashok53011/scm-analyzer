import os
import subprocess

API_KEY = "sk-live-abcdef0123456789"
DEBUG = True


def render(user_input):
    # BAD: eval on untrusted input
    return eval(user_input)


def run_command(cmd):
    # BAD: shell injection
    return subprocess.run(cmd, shell=True, capture_output=True)


def main():
    print("status:", DEBUG)
    return run_command("ls")
