const baseUrl = '/_native/api/call';

async function bridgeCall(method, params = {}) {
    const response = await fetch(baseUrl, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': document.querySelector('meta[name="csrf-token"]')?.content || '',
        },
        body: JSON.stringify({ method, params }),
    });

    if (!response.ok) {
        throw new Error(`Native call failed with status ${response.status}`);
    }

    const result = await response.json();

    if (result.status === 'error') {
        throw new Error(result.message || 'Native call failed');
    }

    return result.data;
}

export const Launcher = {
    apps: async () => (await bridgeCall('Launcher.Apps'))?.apps ?? [],
    open: async (pkg, display = null, activity = null) =>
        (await bridgeCall('Launcher.Open', {
            package: pkg,
            ...(display === null ? {} : { display }),
            ...(activity === null ? {} : { activity }),
        }))?.ok ?? false,
    isDefaultHome: async () => (await bridgeCall('Launcher.IsDefaultHome'))?.default ?? false,
    openHomeSettings: async () => (await bridgeCall('Launcher.OpenHomeSettings'))?.ok ?? false,
};

export default Launcher;
