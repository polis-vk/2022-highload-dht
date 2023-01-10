import argparse
import math
import matplotlib.pyplot as plt
import pathlib
import re
import subprocess
import time

ASYNC_PROFILER_DIR = "/home/asladkii/packages/async-profiler"

WRK2 = "/home/asladkii/packages/wrk2/wrk"

TOP_OUTPUT_DIR = "output"

SLEEP_TIME_SECONDS = 10

OK_LATENCY_LOWER_BOUND = 0.5
OK_LATENCY_UPPER_BOUND = 2

RATE_STEP = 1000
START_RATE = RATE_STEP
END_RATE = 30000


def latency_to_ms(latency):
    if latency.endswith("nanus"):
        return math.inf

    match = re.search(r"([\d\.]+)(\D+)", latency)
    value, time_type = float(match.group(1)), match.group(2)
    if time_type == "ms":
        pass
    elif time_type == "us":
        value /= 1000
    elif time_type == "s":
        value *= 1000
    else:
        raise RuntimeError("unknown time type: " + time_type)
    return value


def extract_latency(wrk_output):
    match = re.search(r"Latency(\s+)(\S+)", wrk_output)

    latency = match.group(2)
    print("latency", latency)

    result = latency_to_ms(latency)
    print(result)

    return result


def requests_to_kb(requests):
    if requests.endswith("nan"):
        return math.inf

    match = re.search(r"([\d\.]+)(\D*)", requests)
    value, requests_type = float(match.group(1)), match.group(2)
    if requests_type.lower() == "k":
        pass
    elif not requests_type:
        value /= 1024
    else:
        raise RuntimeError("unknown requests type: " + requests_type)
    return value


def extract_requests(wrk_output):
    match = re.search(r"Req/Sec(\s+)(\S+)", wrk_output)

    requests = match.group(2)
    print("requests", requests)

    result = requests_to_kb(requests)
    print(result)

    return result


def launch(threads, connections, duration, script, url):
    rates = list(range(START_RATE, END_RATE + 1, RATE_STEP))
    latencies, requests = [], []
    for rate in rates:
        command = f"{WRK2} -t{threads} -c{connections} -d{duration} -R{rate} -s {script} {url}"
        wrk_result = subprocess.check_output(command.split(), text=True, encoding="utf-8")

        print("\n\nrate", rate)
        print(wrk_result)

        latencies.append(extract_latency(wrk_result))
        requests.append(extract_requests(wrk_result))

        time.sleep(SLEEP_TIME_SECONDS)

    return rates, latencies, requests


def plot_latencies_requests(output_dir, rates, latencies, requests, bounds=None):
    fig, (ax1, ax2) = plt.subplots(1, 2)

    fig.set_figwidth(18)
    fig.set_figheight(9)

    ax1.plot(rates, latencies, 'b')
    ax1.set_xlabel("Rate")
    ax1.set_ylabel("Latency, ms")
    ax1.set_ylim(0, 20)
    ax1.set_title("Rate vs Latency")
    if bounds is not None:
        lower, upper = bounds
        ax1.axhline(y=lower, color='r', linestyle='--')
        ax1.axhline(y=upper, color='r', linestyle='--')

    ax2.plot(rates, requests, 'g')
    ax2.set_xlabel("Rate")
    ax2.set_ylabel("Req/Sec, K")
    ax2.set_title("Rate vs Req/Sec")

    fig.tight_layout()
    fig.savefig(output_dir / "plot_put_default.png")


def notable_print(msg):
    print("=============", msg.upper(), "=============")


def get_output_dir(workload_type):
    top_output_dir = pathlib.Path(TOP_OUTPUT_DIR)
    top_output_dir.mkdir(exist_ok=True)

    mx = 0
    for file in top_output_dir.iterdir():
        file_num = int(file.name[:file.name.index("-")])
        mx = max(mx, file_num)

    output_dir = top_output_dir / f"{mx + 1}-{workload_type}"
    output_dir.mkdir()
    return output_dir


def extract_workload_type(script):
    match = re.search(r"([^/]+).lua", script)
    workload_type = match.group(1)
    print("workload_type", workload_type)
    return workload_type


def get_jfr_file_name(output_dir, servername):
    return f"{output_dir}/{servername.lower()}.jfr"


def check_returncode(cp):
    if cp.returncode != 0:
        raise RuntimeError(f"return code {cp.returncode}")


def start_async_profiler(output_dir, servername, event, lock, chunktime):
    command = ["sh", f"{ASYNC_PROFILER_DIR}/profiler.sh"]
    command.extend(["-f", get_jfr_file_name(output_dir, servername)])
    command.extend(["--chunktime", chunktime])
    command.extend(["-e", ",".join([event_type for event_type in ["cpu", "alloc"] if event_type in event])])
    if "lock" in event:
        command.extend(["--lock", lock])
    command.append("start")
    command.append(servername)

    print(" ".join(command))

    cp = subprocess.run(command)
    check_returncode(cp)


def check_error(error):
    if error is not None:
        raise RuntimeError(error)


def stop_async_profiler(servername):
    command = f"sh {ASYNC_PROFILER_DIR}/profiler.sh stop {servername}"
    sp = subprocess.Popen(command.split(), stdout=subprocess.PIPE, text=True, encoding="utf-8")
    output, error = sp.communicate()
    check_error(error)


def build_htmls(output_dir, servername, event):
    prefix = f"java -cp {ASYNC_PROFILER_DIR}/build/converter.jar jfr2heat {get_jfr_file_name(output_dir, servername)}"
    for event_type in event.split(","):
        html_path = output_dir / f"{event_type}.html"
        command = f"{prefix} --{event_type} {html_path}"
        sp = subprocess.Popen(command.split(), stdout=subprocess.PIPE, text=True, encoding="utf-8")
        output, error = sp.communicate()
        check_error(error)
        make_chunks_red(html_path)


def make_chunks_red(html_path: pathlib.Path):
    old_content = html_path.read_text(encoding="utf-8")
    new_content = old_content.replace("const H = Math.random() * 6;", "const H = 0;")
    html_path.write_text(new_content, encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser("""
    Combines features of wrk2 and async-profiler.

    Before using async-profiler must be build (e.g. via make -j) and 
    perf_events must be enabled:
    $ sudo sysctl kernel.perf_event_paranoid=1
    $ sudo sysctl kernel.kptr_restrict=0
    
    """)
    # wrk2 args
    parser.add_argument("-t", "--threads", type=int, help="Number of threads to use")
    parser.add_argument("-c", "--connections", type=int, help="Connections to keep open")
    parser.add_argument("-d", "--duration", type=str, help="Duration of test")
    parser.add_argument("-s", "--script", type=str, help="Load Lua script file")
    parser.add_argument("-u", "--url", type=str, help="url")
    # async-profiler args
    parser.add_argument("-sn", "--servername", type=str, help="server to profile process name")
    parser.add_argument("-e", "--event", type=str, default="cpu,alloc,lock", help="events for async-profiler")
    parser.add_argument("--lock", type=str, default="1ms", help="lock profiling threshold")
    parser.add_argument("--chunktime", type=str, default="1s", help="appxorimate time limits for JFR chunk")
    # extra args
    parser.add_argument("-ib", "--interactivebound", action="store_true",
                        help="enables feature to choose OK_LATENCY_LOWER_BOUND and OK_LATENCY_UPPER_BOUND interactively")

    args = parser.parse_args()

    workload_type = extract_workload_type(args.script)
    output_dir = get_output_dir(workload_type)

    try:
        notable_print("start async profiler")
        start_async_profiler(output_dir, args.servername, args.event, args.lock, args.chunktime)

        notable_print("start launch")
        rates, latencies, requests = launch(args.threads, args.connections, args.duration, args.script, args.url)
        notable_print("end launch")
    finally:
        notable_print("stop async profiler")
        stop_async_profiler(args.servername)

    notable_print("build htmls")
    build_htmls(output_dir, args.servername, args.event)

    print("rates", rates)
    print("latencies", latencies)
    print("requests per second", requests)

    if args.interactivebound:
        bounds = None
        while True:
            plot_latencies_requests(output_dir, rates, latencies, requests, bounds)
            action = input().split()
            if len(action) == 2:
                bounds = [float(x) for x in action]
            elif len(action) == 1 and action[0] == "exit":
                break
            else:
                print("unknown action")
    else:
        plot_latencies_requests(output_dir, rates, latencies, requests,
                                [OK_LATENCY_LOWER_BOUND, OK_LATENCY_UPPER_BOUND])
