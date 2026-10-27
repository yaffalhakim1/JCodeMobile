@echo off
rem Serves the host's jcode projects to the phone on port 7645.
rem Keep this window open while using the project picker.
python "%~dp0projects_bridge.py" --port 7645 %*
