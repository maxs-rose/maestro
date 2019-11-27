import argparse
import json
import os
import subprocess
import sys
from io import open

sys.path.append(os.getcwd() + '/..')

from jarunpacker import jar_unpacker
from resultcheck import check_results
from pathlib import Path
import shutil

parser = argparse.ArgumentParser(prog='PROG', usage='%(prog)s [options]')
parser.add_argument('--jar', help='jar', required=True)
parser.add_argument('--live', help='live output from API', action='store_true')
parser.set_defaults(live=False)

args = parser.parse_args()

classpathDir = jar_unpacker(args.jar)

cliArgs = json.load(open("cli_arguments.json"))

starttime = cliArgs["start_time"]
endtime = cliArgs["end_time"]
outputfile = cliArgs["output_file"]

config = json.load(open("config.json"))
for fmu in config["fmus"]:
    print (fmu)
    name = Path(config["fmus"][fmu])
    src = '../fmus' / name
    dest = Path(classpathDir) / name
    shutil.copy(src, dest)

stream = None
if args.live:
    stream = subprocess.PIPE
else:
    stream = open('api.log', 'w')

classpath = ".:" + str(Path(classpathDir) / Path(
    "BOOT-INF") / Path("lib")) + "/*"

subprocess.run(["java", "-cp", str(
    classpath), "org.intocps.orchestration.coe.CoeMain", "--oneshot", "--configuration", "../config.json", "--result",
                "result.csv", "--starttime", str(
        starttime), "--endtime", str(endtime), "--result", outputfile],
               stdout=stream, stderr=stream, cwd=classpathDir)

# parser.add_argument('--config', help='configuration', required=True)
# parser.add_argument('--csv', help='result csv file', required=True)
# parser.add_argument('--args', help='cli args', required=True)

args = parser.parse_args()

config = json.load(open("config.json", encoding='utf8'))
# cli = json.load(open("cli_arguments", encoding='utf8'))

outputColumns = [c for c in config['connections']]
stepSize = float(config['algorithm']['size'])

# expectedStart = float(cli['start_time'])
# expectedEnd = float(cli['end_time'])

if check_results(outputColumns, classpathDir / Path("result.csv"), starttime, endtime, stepSize) == 1:
    print("Error")
    exit(1)

exit(0)
